package cn.itedus.lottery.domain.strategy.service.draw;

import cn.itedus.lottery.common.Constants;
import cn.itedus.lottery.domain.strategy.model.aggregates.StrategyRich;
import cn.itedus.lottery.domain.strategy.model.req.DrawReq;
import cn.itedus.lottery.domain.strategy.model.res.DrawResult;
import cn.itedus.lottery.domain.strategy.model.vo.AwardRateInfo;
import cn.itedus.lottery.domain.strategy.model.vo.DrawAwardInfo;
import cn.itedus.lottery.domain.strategy.service.algorithm.IDrawAlgorithm;
import cn.itedus.lottery.infrastructure.po.Award;
import cn.itedus.lottery.infrastructure.po.Strategy;
import cn.itedus.lottery.infrastructure.po.StrategyDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: 定义抽象抽奖过程，模板模式
 * @author：小傅哥，微信：fustack
 * @date: 2021/8/28
 * @Copyright：公众号：bugstack虫洞栈 | 博客：https://bugstack.cn - 沉淀、分享、成长，让自己和他人都能有所收获！
 */
    //抽象类定义，子类使用继承此抽象类的定义。来实现相关的抽奖方法
    //extend和implement的区别：extend是用来继承一个类的，而implement是用来实现一个接口的。在这个上下文中，AbstractDrawBase是一个抽象类，它继承了DrawStrategySupport类，并且实现了IDrawExec接口。这意味着AbstractDrawBase可以使用DrawStrategySupport中的方法和属性，同时也必须实现IDrawExec接口中定义的方法（在这里是doDrawExec方法）。通过这种方式，AbstractDrawBase可以提供一些通用的抽奖逻辑，而具体的抽奖算法和排除奖品的逻辑则由子类来实现。
public abstract class AbstractDrawBase extends DrawStrategySupport implements IDrawExec {

    private Logger logger = LoggerFactory.getLogger(AbstractDrawBase.class);

    //定义抽奖的顺序，后面子类继承抽象类也需要准寻这个顺心序来实现抽奖方法
    @Override
    public DrawResult doDrawExec(DrawReq req) {
        // 1. 获取抽奖策略
        //super关键字是用来调用父类的方法或访问父类的成员变量的。在这个上下文中，super.queryStrategyRich(req.getStrategyId())调用了父类DrawStrategySupport中的queryStrategyRich方法，并传递了req.getStrategyId()作为参数。这意味着当前类（AbstractDrawBase）继承了DrawStrategySupport，并且可以使用它的方法来获取抽奖策略的详细信息。
        //获取到strategyId对应的抽奖策略的详细信息，其中包含了抽奖策略的基本信息和抽奖策略详情列表（每个奖品的中奖概率等信息）
        StrategyRich strategyRich = super.queryStrategyRich(req.getStrategyId());
        Strategy strategy = strategyRich.getStrategy();

        // 2. 校验抽奖策略是否已经初始化到内存
        this.checkAndInitRateData(req.getStrategyId(), strategy.getStrategyMode(), strategyRich.getStrategyDetailList());

        //这两步是由子类来实现的，实现queryExcludeAwardIds方法来获取不在抽奖范围内的列表，实现drawAlgorithm方法来执行抽奖算法
        // 3. 获取不在抽奖范围内的列表，包括：奖品库存为空、风控策略、临时调整等
        List<String> excludeAwardIds = this.queryExcludeAwardIds(req.getStrategyId());

        // 4. 执行抽奖算法
        String awardId = this.drawAlgorithm(req.getStrategyId(), drawAlgorithmGroup.get(strategy.getStrategyMode()), excludeAwardIds);

        // 5. 包装中奖结果
        return buildDrawResult(req.getuId(), req.getStrategyId(), awardId);
    }

    //使用抽奖策略来实现有关的问题处理
    /**
     * 获取不在抽奖范围内的列表，包括：奖品库存为空、风控策略、临时调整等，这类数据是含有业务逻辑的，所以需要由具体的实现方决定
     *
     * @param strategyId 策略ID
     * @return 排除的奖品ID集合
     */
    protected abstract List<String> queryExcludeAwardIds(Long strategyId);

    /**
     * 执行抽奖算法
     *
     * @param strategyId      策略ID
     * @param drawAlgorithm   抽奖算法模型
     * @param excludeAwardIds 排除的抽奖ID集合
     * @return 中奖奖品ID
     */
    protected abstract String drawAlgorithm(Long strategyId, IDrawAlgorithm drawAlgorithm, List<String> excludeAwardIds);

    /**
     * 校验抽奖策略是否已经初始化到内存
     *
     * @param strategyId         抽奖策略ID
     * @param strategyMode       抽奖策略模式
     * @param strategyDetailList 抽奖策略详情
     */
    private void checkAndInitRateData(Long strategyId, Integer strategyMode, List<StrategyDetail> strategyDetailList) {

        if (null == strategyId || null == strategyMode) {
            throw new IllegalArgumentException("strategyId 或 strategyMode 不能为空");
        }

        IDrawAlgorithm drawAlgorithm = drawAlgorithmGroup.get(strategyMode);
        if (null == drawAlgorithm) {
            throw new IllegalArgumentException("未找到对应抽奖算法，strategyMode=" + strategyMode);
        }

        /**
         * 非单项概率，不必计入缓存
         * 单项概率，将抽完的奖品改为未中奖，整体概论，将抽完的奖品动态调整成还有的奖品
         * 这里判断如果是整体概论，不缓存，
         */
        /*// 非单项概率，不必存入缓存
        if (!Constants.StrategyMode.SINGLE.getCode().equals(strategyMode)) {
            return;
        }

        IDrawAlgorithm drawAlgorithm = drawAlgorithmGroup.get(strategyMode);

        // 已初始化过的数据，不必重复初始化
        if (drawAlgorithm.isExistRateTuple(strategyId)) {
            return;
        }

        // 解析并初始化中奖概率数据到散列表
        List<AwardRateInfo> awardRateInfoList = new ArrayList<>(strategyDetailList.size());
        for (StrategyDetail strategyDetail : strategyDetailList) {
            awardRateInfoList.add(new AwardRateInfo(strategyDetail.getAwardId(), strategyDetail.getAwardRate()));
        }

        drawAlgorithm.initRateTuple(strategyId, awardRateInfoList);*/

        // 1. 先转换原始概率数据（两种策略都需要）
        List<AwardRateInfo> awardRateInfoList = new ArrayList<>();
        if (null != strategyDetailList && !strategyDetailList.isEmpty()) {
            for (StrategyDetail detail : strategyDetailList) {
                if (null == detail || null == detail.getAwardId() || null == detail.getAwardRate()) {
                    continue;
                }
                awardRateInfoList.add(new AwardRateInfo(detail.getAwardId(), detail.getAwardRate()));
            }
        }

        // 3. ✅ 所有策略都必须先存储原始概率数据（防止空指针）
        drawAlgorithm.initAwardRateInfo(strategyId, awardRateInfoList);

        // 4. 仅单项概率模式需要额外构建概率数组（用于O(1)抽奖）
        if (Constants.StrategyMode.SINGLE.getCode().equals(strategyMode) && !drawAlgorithm.isExistRateTuple(strategyId)) {
            drawAlgorithm.initRateTuple(strategyId, awardRateInfoList);
        }
    }

    /**
     * 包装抽奖结果
     *
     * @param uId        用户ID
     * @param strategyId 策略ID
     * @param awardId    奖品ID，null 情况：并发抽奖情况下，库存临界值1 -> 0，会有用户中奖结果为 null
     * @return 中奖结果
     */
    private DrawResult buildDrawResult(String uId, Long strategyId, String awardId) {
        if (null == awardId) {
            logger.info("执行策略抽奖完成【未中奖】，用户：{} 策略ID：{}", uId, strategyId);
            return new DrawResult(uId, strategyId, Constants.DrawState.FAIL.getCode());
        }

        //中奖了，需要返回奖品信息
        Award award = super.queryAwardInfoByAwardId(awardId);
        DrawAwardInfo drawAwardInfo = new DrawAwardInfo(award.getAwardId(), award.getAwardName());
        logger.info("执行策略抽奖完成【已中奖】，用户：{} 策略ID：{} 奖品ID：{} 奖品名称：{}", uId, strategyId, awardId, award.getAwardName());

        return new DrawResult(uId, strategyId, Constants.DrawState.SUCCESS.getCode(), drawAwardInfo);
    }

}
