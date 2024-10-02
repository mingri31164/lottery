package com.itheima.prize.commons.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.prize.commons.db.entity.CardGameProduct;
import com.itheima.prize.commons.db.entity.CardProduct;
import com.itheima.prize.commons.db.entity.CardProductDto;
import com.itheima.prize.commons.db.service.CardGameProductService;
import com.itheima.prize.commons.db.mapper.CardGameProductMapper;
import com.itheima.prize.commons.db.service.CardProductService;
import com.itheima.prize.commons.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
* @author shawn
* @description 针对表【card_game_product】的数据库操作Service实现
* @createDate 2023-12-26 11:58:48
*/
@Slf4j
@Service
public class CardGameProductServiceImpl extends ServiceImpl<CardGameProductMapper, CardGameProduct>
    implements CardGameProductService{

    @Autowired
    private CardProductService cardProductService;


    public List<CardProductDto> listGameProductsByGameId(int gameid) {

        // 查询活动对应的奖品 ID 和数量
        List<CardGameProduct> gameProducts = query().eq("gameid", gameid).list();

        List<CardProductDto> productDtos = new ArrayList<>();

        for (CardGameProduct gameProduct : gameProducts) {
            // 查询商品表以获取商品详细信息
            CardProduct product = cardProductService.getById(gameProduct.getProductid());

            // 封装到 DTO
            CardProductDto dto = new CardProductDto();
            dto.setId(product.getId());
            dto.setName(product.getName());
            dto.setPic(product.getPic());
            dto.setInfo(product.getInfo());
            dto.setPrice(product.getPrice());
            dto.setAmount(gameProduct.getAmount());

            // 添加到集合中
            productDtos.add(dto);
        }
        return productDtos;
    }
}




