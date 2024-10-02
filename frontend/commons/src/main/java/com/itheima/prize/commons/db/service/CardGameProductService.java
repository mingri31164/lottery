package com.itheima.prize.commons.db.service;

import com.itheima.prize.commons.db.entity.CardGameProduct;
import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.prize.commons.db.entity.CardProductDto;

import java.util.List;

/**
* @author shawn
* @description 针对表【card_game_product】的数据库操作Service
* @createDate 2023-12-26 11:58:48
*/
public interface CardGameProductService extends IService<CardGameProduct> {

    /**
     * 根据 gameid查看活动包含的奖品信息
     * @param gameid
     * @return
     */
    List<CardProductDto> listGameProductsByGameId(int gameid);
}
