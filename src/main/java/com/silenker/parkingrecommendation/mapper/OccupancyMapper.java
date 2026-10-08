package com.silenker.parkingrecommendation.mapper;

import com.silenker.parkingrecommendation.model.entity.OccupancySnapshot;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OccupancyMapper {

    int insert(OccupancySnapshot snapshot);

    /** 快照总数，用于判断是否需要冷启动生成合成历史数据 */
    long count();

    List<OccupancySnapshot> selectAll();
}
