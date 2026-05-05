package com.worm.server.service;

import com.worm.server.model.SessionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    public void execute(String taskType, SessionContext context) {
        logger.info("TaskService executing: taskType={}, sessionId={}", taskType, context.getSessionId());

        switch (taskType) {
            case "generate_ppt":
                logger.info("[generate_ppt] Step 1/5: 分析需求与主题");
                logger.info("[generate_ppt] Step 2/5: 生成PPT大纲");
                logger.info("[generate_ppt] Step 3/5: 生成每页内容");
                logger.info("[generate_ppt] Step 4/5: 构建PPT文件");
                logger.info("[generate_ppt] Step 5/5: 通知用户完成");
                break;

            case "generate_doc":
                logger.info("[generate_doc] Step 1/5: 分析需求");
                logger.info("[generate_doc] Step 2/5: 生成文档大纲");
                logger.info("[generate_doc] Step 3/5: 生成文档内容");
                logger.info("[generate_doc] Step 4/5: 格式化文档");
                logger.info("[generate_doc] Step 5/5: 通知用户完成");
                break;

            case "summarize_meeting":
                logger.info("[summarize_meeting] Step 1/3: 提取关键信息");
                logger.info("[summarize_meeting] Step 2/3: 组织总结内容");
                logger.info("[summarize_meeting] Step 3/3: 输出总结");
                break;

            case "analyze_data":
                logger.info("[analyze_data] Step 1/4: 解析数据需求");
                logger.info("[analyze_data] Step 2/4: 查询数据");
                logger.info("[analyze_data] Step 3/4: 生成分析报告");
                logger.info("[analyze_data] Step 4/4: 通知用户完成");
                break;

            default:
                logger.info("[{}] Executing generic task flow", taskType);
                logger.info("[{}] Step 1/3: 分析请求", taskType);
                logger.info("[{}] Step 2/3: 执行任务", taskType);
                logger.info("[{}] Step 3/3: 输出结果", taskType);
                break;
        }

        logger.info("TaskService complete: taskType={}, sessionId={}", taskType, context.getSessionId());
    }
}
