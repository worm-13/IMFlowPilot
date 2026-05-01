package com.worm.server.mapper;

import com.worm.server.model.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("select id, nickname, created_at as createdAt from `user` where id = #{id}")
    User findById(@Param("id") String id);

    @Insert("insert into `user` (id, nickname, created_at) values (#{id}, #{nickname}, #{createdAt})")
    int insert(User user);
}

