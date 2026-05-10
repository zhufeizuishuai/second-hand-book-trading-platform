package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.api.po.Order;
@Mapper
public interface OrderMapper extends BaseMapper<Order>{
}
