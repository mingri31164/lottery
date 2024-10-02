package com.itheima.prize.msg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.service.CardGameProductService;
import com.itheima.prize.commons.db.service.CardGameRulesService;
import com.itheima.prize.commons.db.service.CardGameService;
import com.itheima.prize.commons.db.service.GameLoadService;
import com.itheima.prize.commons.utils.RedisUtil;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 活动信息预热，每隔1分钟执行一次
 * 查找未来1分钟内（含），要开始的活动
 */
@Component
public class GameTask {
    private final static Logger log = LoggerFactory.getLogger(GameTask.class);
    @Autowired
    private CardGameService gameService;
    @Autowired
    private CardGameProductService gameProductService;
    @Autowired
    private CardGameRulesService gameRulesService;
    @Autowired
    private GameLoadService gameLoadService;
    @Autowired
    private RedisUtil redisUtil;

    @Scheduled(cron = "0 * * * * ?")
    public void execute() {
        System.out.printf("scheduled!"+new Date());
        log.info("缓存预热");
        Date begin = new Date();
        Date end = DateUtils.addMinutes(begin,1);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        //构造查询条件
        LambdaQueryWrapper<CardGame> query_conditions = new LambdaQueryWrapper();
        query_conditions.ge(CardGame::getStarttime, dateFormat.format(begin)).
                lt(CardGame::getStarttime, dateFormat.format(end));
        List<CardGame> gameList = gameService.list(query_conditions);//执行查询条件
        if (gameList.isEmpty()) return;

        for (CardGame game : gameList) {
            String tokenKey = RedisKeys.TOKENS + game.getId();

            Date gameStartTime = game.getStarttime();
            Date gameEndTime = game.getEndtime();
            long expire =gameEndTime.getTime() - gameStartTime.getTime();

            // 缓存活动基本信息
            redisUtil.set(RedisKeys.INFO + game.getId(), game, -1);

            // 缓存策略信息
            List<CardGameRules> cardGameRulesList = gameRulesService.list();
            for (CardGameRules r : cardGameRulesList) {
                redisUtil.hset(RedisKeys.MAXGOAL + game.getId(), r.getUserlevel() + "", r.getGoalTimes(),expire/1000+5);
                redisUtil.hset(RedisKeys.MAXENTER + game.getId(), r.getUserlevel() + "", r.getEnterTimes(),expire/1000+5);
            }
            // 缓存抽奖令牌桶
            List<Long> tokenList = new ArrayList<>();

            // 查询活动对应的奖品ID和数量
            List<CardProductDto> gameProductList = gameLoadService.getByGameId(game.getId());

            // 在活动时间段内生成随机时间戳做令牌
            for (CardProductDto productDto : gameProductList) {


                Long token = generateToken(gameStartTime, gameEndTime);

                // 将令牌加入桶中
                tokenList.add(token);

                //令牌-奖品映射信息
                redisUtil.set(tokenKey+"_"+token,productDto,expire/1000);
            }
            // 按时间戳从小到大排序
            tokenList.sort(Long::compareTo);

            // 将抽奖令牌桶存入 Redis，从右侧入队
            redisUtil.rightPushAll(tokenKey, tokenList);
            redisUtil.expire(tokenKey, expire/1000+5);
        }
    }

    public static Long generateToken(Date gameStartTime, Date gameEndTime){
        // 获取游戏开始和结束时间的时间戳（毫秒）
        long startMillis = gameStartTime.getTime();
        long endMillis = gameEndTime.getTime();

        // 生成随机中奖时间戳（毫秒级）
        long randomStartMillis = ThreadLocalRandom.current().nextLong(startMillis, endMillis);
        // 解决令牌重复问题：将（时间戳*1000+3位随机数）作为令牌（防止时间段短奖品多时重复）。
        // 抽奖时将抽中的令牌/1000，还原真实时间戳
        long duration = endMillis - randomStartMillis;
        long rnd = randomStartMillis + new Random().nextInt((int) duration);
        long token = rnd * 1000 + new Random().nextInt(999);

        return token;
    }
}
