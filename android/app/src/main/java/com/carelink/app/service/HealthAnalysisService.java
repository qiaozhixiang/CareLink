package com.carelink.app.service;

import com.carelink.app.data.local.dao.CheckinDao;
import com.carelink.app.data.local.entity.CheckinRecordEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 健康分析服务
 * 基于历史打卡数据分析健康趋势和风险评估
 */
@Singleton
public class HealthAnalysisService {

    // ==================== 健康指标阈值 ====================
    // 心率
    private static final int HEART_RATE_LOW = 60;
    private static final int HEART_RATE_HIGH = 100;
    private static final int HEART_RATE_CRITICAL_LOW = 50;
    private static final int HEART_RATE_CRITICAL_HIGH = 120;

    // 血氧
    private static final int BLOOD_OXYGEN_LOW = 95;
    private static final int BLOOD_OXYGEN_CRITICAL = 90;

    // 血压
    private static final int SYSTOLIC_NORMAL_HIGH = 130;
    private static final int SYSTOLIC_CRITICAL_HIGH = 140;
    private static final int DIASTOLIC_NORMAL_HIGH = 85;
    private static final int DIASTOLIC_CRITICAL_HIGH = 90;

    // 打卡完成率
    private static final double CHECKIN_RATE_WARNING = 0.7;  // 低于70%警告
    private static final double CHECKIN_RATE_CRITICAL = 0.5; // 低于50%危险

    // ==================== 成员变量 ====================
    private final CheckinDao checkinDao;
    private final ExecutorService executor;

    public interface AnalysisCallback {
        void onSuccess(HealthAnalysisResult result);
        void onError(String message);
    }

    // ==================== 构造函数 ====================
    @Inject
    public HealthAnalysisService(CheckinDao checkinDao) {
        this.checkinDao = checkinDao;
        this.executor = Executors.newSingleThreadExecutor();
    }

    // ==================== 公开方法 ====================

    /**
     * 分析最近N天的健康数据
     */
    public void analyzeHealthData(int days, AnalysisCallback callback) {
        executor.execute(() -> {
            try {
                HealthAnalysisResult result = performAnalysis(days);
                callback.onSuccess(result);
            } catch (Exception e) {
                callback.onError("分析失败: " + e.getMessage());
            }
        });
    }

    /**
     * 获取健康评分（0-100）
     */
    public void getHealthScore(int days, HealthScoreCallback callback) {
        executor.execute(() -> {
            try {
                int score = calculateHealthScore(days);
                callback.onSuccess(score);
            } catch (Exception e) {
                callback.onError("评分失败: " + e.getMessage());
            }
        });
    }

    /**
     * 获取健康趋势
     */
    public void getHealthTrend(int days, TrendCallback callback) {
        executor.execute(() -> {
            try {
                Map<String, TrendData> trends = calculateTrends(days);
                callback.onSuccess(trends);
            } catch (Exception e) {
                callback.onError("趋势分析失败: " + e.getMessage());
            }
        });
    }

    /**
     * 获取风险评估
     */
    public void getRiskAssessment(int days, RiskCallback callback) {
        executor.execute(() -> {
            try {
                RiskAssessment assessment = assessRisks(days);
                callback.onSuccess(assessment);
            } catch (Exception e) {
                callback.onError("风险评估失败: " + e.getMessage());
            }
        });
    }

    // ==================== 核心分析逻辑 ====================

    /**
     * 执行综合健康分析
     */
    private HealthAnalysisResult performAnalysis(int days) {
        HealthAnalysisResult result = new HealthAnalysisResult();

        // 获取日期范围
        long endTime = System.currentTimeMillis();
        long startTime = getDaysAgoMillis(days);

        // 查询打卡记录
        List<CheckinRecordEntity> records = getRecordsInRange(startTime, endTime);

        // 基本统计
        result.totalRecords = records.size();
        result.daysWithRecords = countDaysWithRecords(records);
        result.expectedDays = days;
        result.checkinRate = (double) result.daysWithRecords / days;

        // 分类统计
        Map<String, List<CheckinRecordEntity>> categorizedRecords = categorizeRecords(records);

        // 分析各项指标
        result.heartRateAnalysis = analyzeHeartRate(categorizedRecords.get("HEART_RATE"));
        result.bloodOxygenAnalysis = analyzeBloodOxygen(categorizedRecords.get("BLOOD_OXYGEN"));
        result.bloodPressureAnalysis = analyzeBloodPressure(categorizedRecords.get("BLOOD_PRESSURE"));
        result.medicineAnalysis = analyzeMedicine(categorizedRecords.get("MEDICINE"));

        // 计算综合评分
        result.overallScore = calculateHealthScore(days);

        // 生成建议
        result.suggestions = generateSuggestions(result);

        // 趋势分析
        result.trends = calculateTrends(days);

        // 风险评估
        result.riskAssessment = assessRisks(days);

        return result;
    }

    /**
     * 计算健康评分（0-100）
     */
    private int calculateHealthScore(int days) {
        int score = 100;

        long endTime = System.currentTimeMillis();
        long startTime = getDaysAgoMillis(days);
        List<CheckinRecordEntity> records = getRecordsInRange(startTime, endTime);

        // 打卡率扣分
        double checkinRate = (double) countDaysWithRecords(records) / days;
        if (checkinRate < CHECKIN_RATE_CRITICAL) {
            score -= 30;
        } else if (checkinRate < CHECKIN_RATE_WARNING) {
            score -= 15;
        }

        // 分析异常指标扣分
        Map<String, List<CheckinRecordEntity>> categorized = categorizeRecords(records);

        // 心率异常扣分
        if (categorized.containsKey("HEART_RATE")) {
            List<CheckinRecordEntity> hrRecords = categorized.get("HEART_RATE");
            int abnormalCount = countAbnormalHeartRate(hrRecords);
            double abnormalRate = (double) abnormalCount / hrRecords.size();
            score -= (int) (abnormalRate * 20);
        }

        // 血氧异常扣分
        if (categorized.containsKey("BLOOD_OXYGEN")) {
            List<CheckinRecordEntity> boRecords = categorized.get("BLOOD_OXYGEN");
            int abnormalCount = countAbnormalBloodOxygen(boRecords);
            double abnormalRate = (double) abnormalCount / boRecords.size();
            score -= (int) (abnormalRate * 20);
        }

        // 服药漏服扣分
        if (categorized.containsKey("MEDICINE")) {
            List<CheckinRecordEntity> medRecords = categorized.get("MEDICINE");
            // 简化：假设应该每天服药
            int missedDays = days - countDaysWithRecords(medRecords);
            double missedRate = (double) missedDays / days;
            score -= (int) (missedRate * 15);
        }

        // 确保障报在有效范围内
        return Math.max(0, Math.min(100, score));
    }

    /**
     * 计算健康趋势
     */
    private Map<String, TrendData> calculateTrends(int days) {
        Map<String, TrendData> trends = new HashMap<>();

        long endTime = System.currentTimeMillis();
        long startTime = getDaysAgoMillis(days);

        // 按时间段分组分析
        int halfDays = days / 2;
        long midTime = endTime - (halfDays * 24L * 60 * 60 * 1000);

        List<CheckinRecordEntity> firstHalf = getRecordsInRange(startTime, midTime);
        List<CheckinRecordEntity> secondHalf = getRecordsInRange(midTime, endTime);

        // 心率趋势
        TrendData hrTrend = new TrendData();
        hrTrend.metric = "heartRate";
        hrTrend.firstPeriodAvg = calculateHeartRateAvg(firstHalf);
        hrTrend.secondPeriodAvg = calculateHeartRateAvg(secondHalf);
        hrTrend.trend = calculateTrendDirection(hrTrend.firstPeriodAvg, hrTrend.secondPeriodAvg);
        trends.put("heartRate", hrTrend);

        // 打卡率趋势
        TrendData checkinTrend = new TrendData();
        checkinTrend.metric = "checkinRate";
        checkinTrend.firstPeriodValue = countDaysWithRecords(firstHalf);
        checkinTrend.secondPeriodValue = countDaysWithRecords(secondHalf);
        checkinTrend.firstPeriodAvg = (double) checkinTrend.firstPeriodValue / halfDays;
        checkinTrend.secondPeriodAvg = (double) checkinTrend.secondPeriodValue / halfDays;
        checkinTrend.trend = calculateTrendDirection(checkinTrend.firstPeriodAvg, checkinTrend.secondPeriodAvg);
        trends.put("checkinRate", checkinTrend);

        return trends;
    }

    /**
     * 评估健康风险
     */
    private RiskAssessment assessRisks(int days) {
        RiskAssessment assessment = new RiskAssessment();

        long endTime = System.currentTimeMillis();
        long startTime = getDaysAgoMillis(days);
        List<CheckinRecordEntity> records = getRecordsInRange(startTime, endTime);

        Map<String, List<CheckinRecordEntity>> categorized = categorizeRecords(records);

        // 评估各项风险
        assessment.fallRisk = assessFallRisk(records);
        assessment.medicineMissRisk = assessMedicineMissRisk(categorized.get("MEDICINE"), days);
        assessment.hypertensionRisk = assessHypertensionRisk(categorized.get("BLOOD_PRESSURE"));
        assessment.hypoxiaRisk = assessHypoxiaRisk(categorized.get("BLOOD_OXYGEN"));

        // 综合风险等级
        assessment.overallRiskLevel = calculateOverallRiskLevel(assessment);

        return assessment;
    }

    // ==================== 辅助方法 ====================

    private List<CheckinRecordEntity> getRecordsInRange(long startTime, long endTime) {
        return checkinDao.getRecordsBetween(startTime, endTime);
    }

    private int countDaysWithRecords(List<CheckinRecordEntity> records) {
        if (records == null || records.isEmpty()) return 0;

        Calendar cal = Calendar.getInstance();
        List<Long> recordDays = new ArrayList<>();

        for (CheckinRecordEntity record : records) {
            cal.setTimeInMillis(record.actualTime);
            int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
            int year = cal.get(Calendar.YEAR);
            long dayKey = (long) year * 1000 + dayOfYear;

            if (!recordDays.contains(dayKey)) {
                recordDays.add(dayKey);
            }
        }

        return recordDays.size();
    }

    private Map<String, List<CheckinRecordEntity>> categorizeRecords(List<CheckinRecordEntity> records) {
        Map<String, List<CheckinRecordEntity>> categorized = new HashMap<>();

        for (CheckinRecordEntity record : records) {
            String type = record.type;
            if (!categorized.containsKey(type)) {
                categorized.put(type, new ArrayList<>());
            }
            categorized.get(type).add(record);
        }

        return categorized;
    }

    private long getDaysAgoMillis(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        return cal.getTimeInMillis();
    }

    // ==================== 指标分析方法 ====================

    private MetricAnalysis analyzeHeartRate(List<CheckinRecordEntity> records) {
        MetricAnalysis analysis = new MetricAnalysis();
        analysis.metricName = "心率";
        analysis.unit = "次/分";
        analysis.sampleCount = records != null ? records.size() : 0;

        if (records == null || records.isEmpty()) {
            analysis.status = "无数据";
            return analysis;
        }

        List<Integer> values = new ArrayList<>();
        int abnormalCount = 0;

        for (CheckinRecordEntity record : records) {
            try {
                int value = Integer.parseInt(record.value);
                values.add(value);

                if (value < HEART_RATE_LOW || value > HEART_RATE_HIGH) {
                    abnormalCount++;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (!values.isEmpty()) {
            analysis.average = values.stream().mapToInt(i -> i).average().orElse(0);
            analysis.min = Collections.min(values);
            analysis.max = Collections.max(values);
            analysis.abnormalRate = (double) abnormalCount / values.size();

            if (analysis.abnormalRate < 0.1) {
                analysis.status = "正常";
            } else if (analysis.abnormalRate < 0.3) {
                analysis.status = "轻度异常";
            } else {
                analysis.status = "异常";
            }
        }

        return analysis;
    }

    private MetricAnalysis analyzeBloodOxygen(List<CheckinRecordEntity> records) {
        MetricAnalysis analysis = new MetricAnalysis();
        analysis.metricName = "血氧";
        analysis.unit = "%";
        analysis.sampleCount = records != null ? records.size() : 0;

        if (records == null || records.isEmpty()) {
            analysis.status = "无数据";
            return analysis;
        }

        List<Integer> values = new ArrayList<>();
        int abnormalCount = 0;

        for (CheckinRecordEntity record : records) {
            try {
                int value = Integer.parseInt(record.value);
                values.add(value);

                if (value < BLOOD_OXYGEN_LOW) {
                    abnormalCount++;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (!values.isEmpty()) {
            analysis.average = values.stream().mapToInt(i -> i).average().orElse(0);
            analysis.min = Collections.min(values);
            analysis.max = Collections.max(values);
            analysis.abnormalRate = (double) abnormalCount / values.size();

            if (analysis.abnormalRate < 0.1) {
                analysis.status = "正常";
            } else if (analysis.abnormalRate < 0.3) {
                analysis.status = "轻度异常";
            } else {
                analysis.status = "异常";
            }
        }

        return analysis;
    }

    private MetricAnalysis analyzeBloodPressure(List<CheckinRecordEntity> records) {
        MetricAnalysis analysis = new MetricAnalysis();
        analysis.metricName = "血压";
        analysis.unit = "mmHg";
        analysis.sampleCount = records != null ? records.size() : 0;

        if (records == null || records.isEmpty()) {
            analysis.status = "无数据";
            return analysis;
        }

        List<Integer> systolicValues = new ArrayList<>();
        List<Integer> diastolicValues = new ArrayList<>();
        int abnormalCount = 0;

        for (CheckinRecordEntity record : records) {
            try {
                String[] parts = record.value.split("/");
                if (parts.length == 2) {
                    int systolic = Integer.parseInt(parts[0]);
                    int diastolic = Integer.parseInt(parts[1]);
                    systolicValues.add(systolic);
                    diastolicValues.add(diastolic);

                    if (systolic > SYSTOLIC_NORMAL_HIGH || diastolic > DIASTOLIC_NORMAL_HIGH) {
                        abnormalCount++;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        if (!systolicValues.isEmpty()) {
            analysis.average = systolicValues.stream().mapToInt(i -> i).average().orElse(0);
            analysis.min = Collections.min(systolicValues);
            analysis.max = Collections.max(systolicValues);
            analysis.abnormalRate = (double) abnormalCount / systolicValues.size();

            if (analysis.abnormalRate < 0.1) {
                analysis.status = "正常";
            } else if (analysis.abnormalRate < 0.3) {
                analysis.status = "轻度异常";
            } else {
                analysis.status = "异常";
            }
        }

        return analysis;
    }

    private MetricAnalysis analyzeMedicine(List<CheckinRecordEntity> records) {
        MetricAnalysis analysis = new MetricAnalysis();
        analysis.metricName = "服药";
        analysis.sampleCount = records != null ? records.size() : 0;
        analysis.status = "正常";

        if (records == null || records.isEmpty()) {
            analysis.status = "无记录";
            return analysis;
        }

        return analysis;
    }

    // ==================== 风险评估方法 ====================

    private int assessFallRisk(List<CheckinRecordEntity> records) {
        // 基于打卡频率和规律性评估跌倒风险
        int risk = 0;

        // 打卡频率低
        double checkinRate = (double) countDaysWithRecords(records) / 7;
        if (checkinRate < 0.5) risk += 3;

        return Math.min(5, risk);
    }

    private int assessMedicineMissRisk(List<CheckinRecordEntity> records, int days) {
        if (records == null) return 3;

        int missedDays = days - countDaysWithRecords(records);
        double missedRate = (double) missedDays / days;

        if (missedRate > 0.5) return 5;
        if (missedRate > 0.3) return 3;
        if (missedRate > 0.1) return 1;
        return 0;
    }

    private int assessHypertensionRisk(List<CheckinRecordEntity> records) {
        if (records == null || records.isEmpty()) return 0;

        int highCount = 0;
        for (CheckinRecordEntity record : records) {
            try {
                String[] parts = record.value.split("/");
                if (parts.length == 2) {
                    int systolic = Integer.parseInt(parts[0]);
                    if (systolic > SYSTOLIC_NORMAL_HIGH) {
                        highCount++;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        double highRate = (double) highCount / records.size();
        if (highRate > 0.5) return 5;
        if (highRate > 0.3) return 3;
        if (highRate > 0.1) return 1;
        return 0;
    }

    private int assessHypoxiaRisk(List<CheckinRecordEntity> records) {
        if (records == null || records.isEmpty()) return 0;

        int lowCount = 0;
        for (CheckinRecordEntity record : records) {
            try {
                int value = Integer.parseInt(record.value);
                if (value < BLOOD_OXYGEN_LOW) {
                    lowCount++;
                }
            } catch (NumberFormatException ignored) {}
        }

        double lowRate = (double) lowCount / records.size();
        if (lowRate > 0.3) return 5;
        if (lowRate > 0.1) return 3;
        if (lowRate > 0) return 1;
        return 0;
    }

    private int calculateOverallRiskLevel(RiskAssessment assessment) {
        int totalRisk = assessment.fallRisk
                + assessment.medicineMissRisk
                + assessment.hypertensionRisk
                + assessment.hypoxiaRisk;

        if (totalRisk >= 10) return 5;
        if (totalRisk >= 7) return 4;
        if (totalRisk >= 4) return 3;
        if (totalRisk >= 2) return 2;
        return 1;
    }

    // ==================== 建议生成 ====================

    private List<String> generateSuggestions(HealthAnalysisResult result) {
        List<String> suggestions = new ArrayList<>();

        // 打卡率建议
        if (result.checkinRate < CHECKIN_RATE_WARNING) {
            suggestions.add("打卡频率偏低，建议坚持每日打卡以便更好地监测健康状况");
        }

        // 心率建议
        if (result.heartRateAnalysis != null && result.heartRateAnalysis.abnormalRate > 0.2) {
            suggestions.add("心率异常情况较多，建议咨询医生或进行详细检查");
        }

        // 血压建议
        if (result.bloodPressureAnalysis != null && result.bloodPressureAnalysis.abnormalRate > 0.2) {
            suggestions.add("血压偏高，请注意低盐饮食并定期监测");
        }

        // 血氧建议
        if (result.bloodOxygenAnalysis != null && result.bloodOxygenAnalysis.abnormalRate > 0.1) {
            suggestions.add("血氧偏低，建议进行呼吸功能检查");
        }

        // 服药建议
        if (result.medicineAnalysis != null && "无记录".equals(result.medicineAnalysis.status)) {
            suggestions.add("暂无服药记录，请按时服药并打卡记录");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("健康状况良好，请继续保持良好的生活习惯");
        }

        return suggestions;
    }

    // ==================== 辅助计算 ====================

    private int countAbnormalHeartRate(List<CheckinRecordEntity> records) {
        if (records == null) return 0;
        int count = 0;
        for (CheckinRecordEntity record : records) {
            try {
                int value = Integer.parseInt(record.value);
                if (value < HEART_RATE_LOW || value > HEART_RATE_HIGH) {
                    count++;
                }
            } catch (NumberFormatException ignored) {}
        }
        return count;
    }

    private int countAbnormalBloodOxygen(List<CheckinRecordEntity> records) {
        if (records == null) return 0;
        int count = 0;
        for (CheckinRecordEntity record : records) {
            try {
                int value = Integer.parseInt(record.value);
                if (value < BLOOD_OXYGEN_LOW) {
                    count++;
                }
            } catch (NumberFormatException ignored) {}
        }
        return count;
    }

    private double calculateHeartRateAvg(List<CheckinRecordEntity> records) {
        if (records == null || records.isEmpty()) return 0;
        int sum = 0;
        int count = 0;
        for (CheckinRecordEntity record : records) {
            try {
                sum += Integer.parseInt(record.value);
                count++;
            } catch (NumberFormatException ignored) {}
        }
        return count > 0 ? (double) sum / count : 0;
    }

    private String calculateTrendDirection(double first, double second) {
        double diff = second - first;
        double threshold = first * 0.1; // 10%变化阈值

        if (diff > threshold) return "上升";
        if (diff < -threshold) return "下降";
        return "平稳";
    }

    // ==================== 回调接口 ====================

    public interface HealthScoreCallback {
        void onSuccess(int score);
        void onError(String message);
    }

    public interface TrendCallback {
        void onSuccess(Map<String, TrendData> trends);
        void onError(String message);
    }

    public interface RiskCallback {
        void onSuccess(RiskAssessment assessment);
        void onError(String message);
    }

    // ==================== 数据类 ====================

    /**
     * 健康分析结果
     */
    public static class HealthAnalysisResult {
        public int totalRecords;
        public int daysWithRecords;
        public int expectedDays;
        public double checkinRate;
        public int overallScore;

        public MetricAnalysis heartRateAnalysis;
        public MetricAnalysis bloodOxygenAnalysis;
        public MetricAnalysis bloodPressureAnalysis;
        public MetricAnalysis medicineAnalysis;

        public List<String> suggestions;
        public Map<String, TrendData> trends;
        public RiskAssessment riskAssessment;
    }

    /**
     * 指标分析
     */
    public static class MetricAnalysis {
        public String metricName;
        public String unit;
        public int sampleCount;
        public double average;
        public int min;
        public int max;
        public double abnormalRate;
        public String status;
    }

    /**
     * 趋势数据
     */
    public static class TrendData {
        public String metric;
        public double firstPeriodAvg;
        public double secondPeriodAvg;
        public double firstPeriodValue;
        public double secondPeriodValue;
        public String trend;
    }

    /**
     * 风险评估
     */
    public static class RiskAssessment {
        public int fallRisk;
        public int medicineMissRisk;
        public int hypertensionRisk;
        public int hypoxiaRisk;
        public int overallRiskLevel;
    }
}
