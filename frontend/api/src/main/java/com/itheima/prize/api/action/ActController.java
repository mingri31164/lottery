package com.itheima.prize.api.action;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.itheima.prize.api.config.LuaScript;
import com.itheima.prize.commons.config.RabbitKeys;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.mapper.CardGameMapper;
import com.itheima.prize.commons.db.service.CardGameRulesService;
import com.itheima.prize.commons.db.service.CardGameService;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.RedisUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.type.TypeReference;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.spring.web.json.Json;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/act")
@Api(tags = {"抽奖模块"})
public class ActController {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private CardGameRulesService cardGameRulesService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private LuaScript luaScript;

    static int defaultMaxEnterTime = 10;
    static int defaultMaxGoalTime = 20;

    @GetMapping("/limits/{gameid}")
    @ApiOperation(value = "剩余次数")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult<Object> limits(@PathVariable int gameid, HttpServletRequest request){
        //获取活动基本信息
        CardGame game = (CardGame) redisUtil.get(RedisKeys.INFO+gameid);
        if (game == null){
            return new ApiResult<>(-1,"活动未加载",null);
        }
        //获取当前用户
        HttpSession session = request.getSession();
        CardUser user = (CardUser) session.getAttribute("user");
        if (user == null){
            return new ApiResult(-1,"未登陆",null);
        }
        //用户可抽奖次数
        Integer enter = (Integer) redisUtil.get(RedisKeys.USERENTER+gameid+"_"+user.getId());
        if (enter == null){
            enter = 0;
        }
        //根据会员等级，获取本活动允许的最大抽奖次数
        Integer maxenter = (Integer) redisUtil.hget(RedisKeys.MAXENTER+gameid,user.getLevel()+"");
        //如果没设置，默认为0，即：不限制次数
        maxenter = maxenter==null ? 0 : maxenter;

        //用户已中奖次数
        Integer count = (Integer) redisUtil.get(RedisKeys.USERHIT+gameid+"_"+user.getId());
        if (count == null){
            count = 0;
        }
        //根据会员等级，获取本活动允许的最大中奖数
        Integer maxcount = (Integer) redisUtil.hget(RedisKeys.MAXGOAL+gameid,user.getLevel()+"");
        //如果没设置，默认为0，即：不限制次数
        maxcount = maxcount==null ? 0 : maxcount;

        //幸运转盘类，先给用户随机剔除，再获取令牌，有就中，没有就说明抢光了
        //一般这种情况会设置足够的商品，卡在随机上
        Integer randomRate = (Integer) redisUtil.hget(RedisKeys.RANDOMRATE+gameid,user.getLevel()+"");
        if (randomRate == null){
            randomRate = 100;
        }

        Map map = new HashMap();
        map.put("maxenter",maxenter);
        map.put("enter",enter);
        map.put("maxcount",maxcount);
        map.put("count",count);
        map.put("randomRate",randomRate);

        return new ApiResult<>(1,"成功",map);
    }
    @GetMapping("/go/{gameid}")
    @ApiOperation(value = "抽奖")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult<Object> act(@PathVariable int gameid, HttpServletRequest request){
        log.info("当前时间");
        log.info(String.valueOf(new Date().getTime()));
        Date now = new Date();

        CardUser user = (CardUser) request.getSession().getAttribute("user");
        if (user == null){
            return new ApiResult(-1,"未登陆",null);
        }
        CardGame gameInfo = (CardGame) redisUtil.get(RedisKeys.INFO+gameid);
        if (gameInfo == null || now.before(gameInfo.getStarttime())){
            return new ApiResult(-1,"活动未开始",null);
        }
        if (now.after(gameInfo.getEndtime())){
            return new ApiResult(-1,"活动已结束",null);
        }

        //key值过期时间
        Long expire = gameInfo.getEndtime().getTime()-now.getTime();

        //判断用户已抽奖次数
        //活动最大可抽奖次数
        Integer gameMaxEnter = (Integer) redisUtil.hget(RedisKeys.MAXENTER+gameid,user.getLevel()+"");
        if(gameMaxEnter == null) gameMaxEnter = defaultMaxEnterTime;

        Integer userEnterTime = (Integer) redisUtil.get(RedisKeys.USERENTER + gameid + "_" + user.getId());
        if (null == userEnterTime){
            redisUtil.set(RedisKeys.USERENTER+gameid+"_"+user.getId(),1,expire);
            //用户参加的活动通过该队列投放
            CardUserGame cardUserGame = new CardUserGame();
            cardUserGame.setUserid(user.getId());
            cardUserGame.setGameid(gameid);
            cardUserGame.setCreatetime(new Date());
            rabbitTemplate.convertAndSend(RabbitKeys.QUEUE_PLAY,JSON.toJSONString(cardUserGame));
        }
        else if (userEnterTime >= gameMaxEnter){
            return new ApiResult(-1,"您的抽奖次数已用完",null);
        }
        //已抽奖次数+1
        redisUtil.incr(RedisKeys.USERENTER+gameid+"_"+user.getId(),1);


        //判断用户已中奖次数
        //活动最大中奖次数
        Integer gameMaxGoal = (Integer) redisUtil.hget(RedisKeys.MAXGOAL+gameid,user.getLevel()+"");
        if (gameMaxGoal == null) gameMaxGoal = defaultMaxGoalTime;

        Integer userGoalTimes = (Integer) redisUtil.get(RedisKeys.USERHIT+gameid+"_"+user.getId());
        if (!redisUtil.hasKey(RedisKeys.USERHIT+gameid+"_"+user.getId())){
            redisUtil.set(RedisKeys.USERHIT+gameid+"_"+user.getId(),0,expire);
        }

        if (now.before(gameInfo.getStarttime())) {
            return new ApiResult(-1,"活动未开始",null);
        }
        else if (now.after(gameInfo.getEndtime())){
            return new ApiResult(-1,"活动已结束",null);
        }

        if (userGoalTimes >= gameMaxEnter){
            return new ApiResult(-1,"您的抽奖次数已用完",null);
        }

        if (userGoalTimes >= gameMaxGoal){
            return new ApiResult(-1,"您已达最大中奖数",null);
        }

        Long result = luaScript.tokenCheck(RedisKeys.TOKENS+gameid,now.getTime()+"");
        if (result == 1){
            return new ApiResult(0,"奖品已抽光！",null);
        }
        else if (result == 0){
            return new ApiResult(0,"未中奖",null);
        }
        else {
            //中奖
            redisUtil.incr(RedisKeys.USERHIT + gameid + "_" + user.getId(), 1);
            //用户中奖后的信息及中的奖品通过该队列投放
            String cardProductKey = RedisKeys.TOKEN + gameid + "_" + result;
            CardProductDto cardProductDto = (CardProductDto) redisUtil.get(cardProductKey);
            CardUserHit cardUserHit = new CardUserHit();
            cardUserHit.setUserid(user.getId());
            cardUserHit.setGameid(gameid);
            cardUserHit.setProductid(cardProductDto.getId());
            cardUserHit.setHittime(new Date());
            rabbitTemplate.convertAndSend(RabbitKeys.QUEUE_HIT, JSON.toJSONString(cardUserHit));

            return new ApiResult<>(1, "恭喜中奖", cardProductDto);
        }
    }

    @GetMapping("/info/{gameid}")
    @ApiOperation(value = "查询缓存信息")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult info(@PathVariable int gameid){
        String tokenKey = RedisKeys.TOKENS+gameid;
        Map map = new HashMap();
        Map maxGoals = new HashMap();
        Map maxEnters = new HashMap<>();
        // 创建一个 LinkedHashMap 以保持插入顺序
        Map<String, CardProductDto> productMap = new LinkedHashMap<>();

        //获取活动基本信息
        CardGame game = (CardGame) redisUtil.get(RedisKeys.INFO+gameid);
        if (game == null){
            return new ApiResult<>(-1,"活动未加载",null);
        }
        //获取活动策略
        List<CardGameRules> cardGameRulesList = cardGameRulesService.lambdaQuery()
                .eq(CardGameRules::getGameid,gameid).list();

        for (CardGameRules r : cardGameRulesList) {
            Integer maxgoal = (Integer) redisUtil.hget(RedisKeys.MAXGOAL + game.getId(), r.getUserlevel() + "");
            Integer maxenter = (Integer) redisUtil.hget(RedisKeys.MAXENTER + game.getId(), r.getUserlevel() + "");

            maxGoals.put(r.getUserlevel()+"",maxgoal);
            maxEnters.put(r.getUserlevel()+"",maxenter);
        }

        List<Long> tokenList = redisTemplate.opsForList().range(RedisKeys.TOKENS+gameid,0,-1);

            for (Long token : tokenList) { //遍历所有时间戳
                //还原真实时间戳
                long realTimeStamp = token/1000;
                // 将时间戳转换为可读的日期时间格式
                Date randomDate = new Date(realTimeStamp);

                // 创建 SimpleDateFormat 对象，设置日期格式（精确到毫秒）
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

               // 将 Date 对象格式化为可读的日期时间字符串
                String formattedDate = sdf.format(randomDate);

                CardProductDto productDto = (CardProductDto) redisUtil.get(RedisKeys.TOKEN+gameid+"_"+token);

                // 如果需要将 product 的所有内容放入 productMap
                productMap.put(formattedDate, productDto);
            }

        map.put(RedisKeys.INFO+gameid,game);
        map.put(RedisKeys.TOKENS+gameid,productMap);
        map.put(RedisKeys.MAXGOAL+gameid,maxGoals);
        map.put(RedisKeys.MAXENTER+gameid,maxEnters);

        return new ApiResult(200,"成功",map);
    }
}
