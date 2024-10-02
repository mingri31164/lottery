package com.itheima.prize.commons.db.mapper;

import com.itheima.prize.commons.db.entity.CardGame;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author shawn
* @description 针对表【card_game】的数据库操作Mapper
* @createDate 2023-12-26 11:58:48
* @Entity com.itheima.prize.commons.db.entity.CardGame
*/
public interface CardGameMapper extends BaseMapper<CardGame> {

    /**
     * 查询1分钟内的活动
     *
     * @return
     */
    @Select("select * from card_game where starttime>#{begin} and starttime<=#{end}")
    List<CardGame> selectAboutToStartGames(LocalDateTime begin, LocalDateTime end);
}




