package com.itheima.prize.commons.db.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.constant.MessageConstant;
import com.itheima.prize.commons.constant.UserConstant;
import com.itheima.prize.commons.db.entity.CardUser;
import com.itheima.prize.commons.db.service.CardUserService;
import com.itheima.prize.commons.db.mapper.CardUserMapper;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.PasswordUtil;
import com.itheima.prize.commons.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/**
* @author shawn
* @description 针对表【card_user(会员信息表)】的数据库操作Service实现
* @createDate 2023-12-26 11:58:48
*/
@Service
public class CardUserServiceImpl extends ServiceImpl<CardUserMapper, CardUser>
    implements CardUserService{

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 用户登录
     * @param request
     * @param account
     * @param password
     * @return CardUser user
     */
    public ApiResult login(HttpServletRequest request, String account, String password) {

        String ipAddress = request.getRemoteAddr(); // 获取用户IP地址
        String attemptsKey = RedisKeys.USERLOGINTIMES + ipAddress;

        //1、 检查是否被锁定
        if (redisUtil.hasKey(attemptsKey) && (int) redisUtil.get(attemptsKey)
                >= UserConstant.Login_MAX_ATTEMPTS) {
            return new ApiResult(0, MessageConstant.LOGIN_LOCKED, null);
        }
        //2、根据用户名查询数据库中的数据
        CardUser user = query().eq("uname", account).one();

        //对前端传过来的明文密码进行md5加密处理
        String hashedPassword = PasswordUtil.md5(password);

        //3、处理各种异常情况（用户名不存在、密码错误、账号被锁定）
        if (user == null || !user.getPasswd().equals(hashedPassword)) {
            //如果是第一次登录错误，设redis中attemptsKey的值为1
            if (!redisUtil.hasKey(attemptsKey)){
                redisUtil.set(attemptsKey, 1);
            }
            // 增加错误次数
            redisUtil.incr(attemptsKey, 1);
            redisUtil.expire(attemptsKey, 300);//10分钟内
            return new ApiResult(0, MessageConstant.LOGIN_FAILED, null);
        }

        //4、 登录成功，清除错误尝试次数
        redisUtil.del(attemptsKey);

        //5、返回ApiResult对象
        return new ApiResult(1, "登录成功", user);
    }
}




