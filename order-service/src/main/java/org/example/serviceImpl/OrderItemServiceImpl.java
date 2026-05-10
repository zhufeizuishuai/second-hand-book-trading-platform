package org.example.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.api.po.OrderItem;
import org.example.mapper.OrderItemMapper;
import org.example.service.OrderItemService;
import org.springframework.stereotype.Service;

@Service

public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem> implements OrderItemService {
}
