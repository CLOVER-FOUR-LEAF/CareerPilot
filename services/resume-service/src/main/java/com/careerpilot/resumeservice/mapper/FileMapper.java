package com.careerpilot.resumeservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careerpilot.resumeservice.entity.FileRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileMapper extends BaseMapper<FileRecord> {
}
