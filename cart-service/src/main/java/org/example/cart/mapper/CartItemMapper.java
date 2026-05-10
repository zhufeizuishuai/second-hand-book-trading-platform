package org.example.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.api.po.CartItem;

@Mapper
public interface CartItemMapper  extends BaseMapper<CartItem> {
}
