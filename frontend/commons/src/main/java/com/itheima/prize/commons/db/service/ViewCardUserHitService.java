package com.itheima.prize.commons.db.service;

import com.itheima.prize.commons.db.entity.CardUser;
import com.itheima.prize.commons.db.entity.ViewCardUserHit;
import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.prize.commons.utils.PageBean;

/**
* @author shawn
* @description 针对表【view_card_user_hit】的数据库操作Service
* @createDate 2023-12-26 11:58:48
*/
public interface ViewCardUserHitService extends IService<ViewCardUserHit> {

    /**
     * 分页查询用户奖品信息
     *
     * @param gameid
     * @param curpage
     * @param limit
     * @param cardUser
     * @return
     */
    PageBean<ViewCardUserHit> pageUserPrize(int gameid, int curpage, int limit, CardUser cardUser);

    /**
     * 分页查询活动中奖列表
     * @param gameid
     * @param curpage
     * @param limit
     * @return
     */
    PageBean<ViewCardUserHit> pageUserHit(int gameid, int curpage, int limit);
}
