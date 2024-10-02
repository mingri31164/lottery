package com.itheima.prize.commons.db.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.prize.commons.db.entity.CardUser;
import com.itheima.prize.commons.db.entity.ViewCardUserHit;
import com.itheima.prize.commons.db.service.ViewCardUserHitService;
import com.itheima.prize.commons.db.mapper.ViewCardUserHitMapper;
import com.itheima.prize.commons.utils.PageBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
* @author shawn
* @description 针对表【view_card_user_hit】的数据库操作Service实现
* @createDate 2023-12-26 11:58:48
*/
@Slf4j
@Service
public class ViewCardUserHitServiceImpl extends ServiceImpl<ViewCardUserHitMapper, ViewCardUserHit>
    implements ViewCardUserHitService{

    @Autowired
    private ViewCardUserHitMapper ViewCardUserHitMapper;

    /**
     * 分页查询用户奖品信息
     * @param gameid
     * @param curpage
     * @param limit
     * @param cardUser
     * @return
     */
    public PageBean<ViewCardUserHit> pageUserPrize(int gameid, int curpage, int limit, CardUser cardUser) {
        Page<ViewCardUserHit> page = new Page();

        log.info(String.valueOf(gameid));

        QueryWrapper<ViewCardUserHit> gameQueryWrapper = new QueryWrapper<>();
        if (gameid == -1){
            gameQueryWrapper.eq("userid", cardUser.getId());
        }
        else {
           gameQueryWrapper.eq("gameid", gameid).eq("userid", cardUser.getId());
        }

        IPage result = page(page, gameQueryWrapper);

        PageBean<ViewCardUserHit> pageBean = new PageBean<>
                (curpage, limit, result.getTotal(), result.getRecords());
        return pageBean;
    }

    /**
     * 分页查询活动中奖列表
     * @param gameid
     * @param curpage
     * @param limit
     * @return
     */
    public PageBean<ViewCardUserHit> pageUserHit(int gameid, int curpage, int limit) {
        Page<ViewCardUserHit> page = new Page();

        log.info(String.valueOf(gameid));

        QueryWrapper<ViewCardUserHit> gameQueryWrapper = new QueryWrapper<>();
        if(gameid > 0){
            gameQueryWrapper.eq("gameid", gameid);
        }

        IPage result = page(page, gameQueryWrapper);

        PageBean<ViewCardUserHit> pageBean = new PageBean<>
                (curpage, limit, result.getTotal(), result.getRecords());
        return pageBean;
    }
}




