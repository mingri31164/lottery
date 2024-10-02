package com.itheima.prize.commons.db.service;

import com.itheima.prize.commons.db.entity.CardUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.prize.commons.utils.ApiResult;

import javax.servlet.http.HttpServletRequest;

/**
* @author shawn
* @description 针对表【card_user(会员信息表)】的数据库操作Service
* @createDate 2023-12-26 11:58:48
*/
public interface CardUserService extends IService<CardUser> {

    /**
     * 用户登录
     * @param request
     * @param account
     * @param password
     * @return
     */
    ApiResult login(HttpServletRequest request, String account, String password);
}
