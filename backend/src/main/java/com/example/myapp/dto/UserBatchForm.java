package com.example.myapp.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除用户的请求体。
 *
 * <p>为什么限制条数：一次删几千个用户会把数据库连接占满、把缓存失效刷爆，
 * 而且**删错了没法撤销**（逻辑删除虽然可恢复，但接口层面没有恢复入口）。
 * 200 是「够用且不至于失控」的量级；真要批量导入/清理，应该走专门的运维脚本。
 */
@Data
public class UserBatchForm {

    @NotEmpty(message = "请至少选择一个用户")
    @Size(max = 200, message = "一次最多删除 200 个用户")
    private List<Long> ids;
}
