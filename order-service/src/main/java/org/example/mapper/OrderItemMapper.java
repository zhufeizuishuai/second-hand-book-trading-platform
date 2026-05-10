package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.api.po.OrderItem;
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
