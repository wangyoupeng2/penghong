package com.hmdp.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @Author：yep
 * @Project：hm-dianping
 * @name：ScrollRedult
 * @Date：2024/3/29 15:20
 * @Filename：ScrollRedult
 */
@Data
@Builder
public class ScrollRedult {
    private List<?> list;
    private Long mintime;
    private Integer offset;
}
