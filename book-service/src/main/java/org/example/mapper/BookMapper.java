package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.po.Book;

@Mapper
public interface BookMapper extends BaseMapper<Book> {
}
