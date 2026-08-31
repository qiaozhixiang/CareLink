package com.carelink.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 农历历法服务（基于精简的天干地支算法）
 * 支持公历转农历、节气、宜忌
 */
@Service
public class LunarCalendarService {

    // ===== 天干地支 =====
    private static final String[] TIAN_GAN = {"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    private static final String[] DI_ZHI   = {"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final String[] SHENG_XIAO = {"鼠","牛","虎","兔","龙","蛇","马","羊","猴","鸡","狗","猪"};

    // ===== 二十四节气（公历日期，约值）=====
    private static final String[] JIE_QI = {
            "小寒","大寒","立春","雨水","惊蛰","春分",
            "清明","谷雨","立夏","小满","芒种","夏至",
            "小暑","大暑","立秋","处暑","白露","秋分",
            "寒露","霜降","立冬","小雪","大雪","冬至"
    };

    // 节气大致日期（每月两个，day 为约值）
    private static final int[][] JIE_QI_DAYS = {
            {6,21}, {4,19}, {3,18}, {5,20}, {5,20}, {20,21},
            {4,20}, {19,20}, {5,21}, {20,21}, {5,21}, {21,22},
            {6,22}, {7,22}, {7,23}, {22,23}, {7,23}, {23,24},
            {8,23}, {8,23}, {7,22}, {7,22}, {7,22}, {21,22}
    };

    // ===== 农历数据（1900-2100年，每年2字节）=====
    // 每个元素的 bit 含义：bit15=1闰年，bit0-4=闰月天数，其他12位=每月大小（15=大月30天，14=小月29天）
    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x05a0c, 0x0d6a0,
            0x1dab6, 0x0b560, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, 0x0d520, 0x0dd55,
            0x0b5a0, 0x056d0, 0x04ae0, 0x0a670, 0x0e4f0, 0x0ea50, 0x06a95, 0x05ad0, 0x02b60, 0x18686,
            0x09ad0, 0x0a2d0, 0x0d2b2, 0x0b950, 0x0b550, 0x055c0, 0x0b570, 0x25370, 0x052b0, 0x0a9b0,
            0x0a4b0, 0x0b4b7, 0x06a50, 0x06d40, 0x0aea1, 0x0ab60, 0x09570, 0x04af5, 0x04970, 0x064b0,
            0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, 0x0c960, 0x0d954, 0x0d4a0,
            0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, 0x0a950, 0x0b4a0, 0x0baa4,
            0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, 0x07954, 0x06aa0, 0x0ad50,
            0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, 0x05aa0, 0x076a3, 0x096d0
    };

    // ===== 宜忌字典 =====
    private static final String[] YI_KEYWORDS = {"祭祀","沐浴","扫舍","入宅","出行","订盟","纳采","嫁娶","安床","解除","拆卸","动土","破土","修造","起基","定磉","安机械","立券","交易","纳财","开光","竖柱","上梁","开仓","出货财","置产","筑堤","教牛马","造屋","合帐","栽种","牧养","纳畜","缝绣","斋醮","焚香","作灶","治病","针灸","探病"};
    private static final String[] JI_KEYWORDS = {"嫁娶","动土","破土","安葬","开市","交易","立券","出货财","置产","入宅","出行","移徙","开光","针灸","伐木","塞穴","破屋","坏垣","求医","治病","安床","扫舍","取渔","栽种"};

    private static final String[] JIA_ZHI_YI = {"嫁娶","出行","搬家","装修","开业","动土","祈福","订盟","出火","拆卸","修造","动土","起基","定磉","开渠","平治道涂","造屋","合帐","竖柱","上梁","开仓","出货财","置产","纳财","立券","交易","开光","纳畜","牧养","栽种","针灸","教牛马","造屋","入宅","移徙","安床","拆卸","动土","破土","修造","起基","安机械","立券","交易","纳财","开光","竖柱","上梁","开仓","出货财","置产","筑堤","造仓","纳畜","缝绣","斋醮","焚香","作灶","治病","针灸","探病"};
    private static final String[] JIA_ZHI_JI = {"开市","安床","出行","移徙","祭祀","祈福","嫁娶","动土","破土","安葬"};
    private static final String[] YI_CHEN_ZHI = {"出行","移徙","搬家","装修","开业","动土","订盟","纳采","嫁娶","入宅","起基","定磉","开渠","造仓","置产","开市","立券","交易","纳财","开光","竖柱","上梁","开仓","出货财","栽种","纳畜","牧养","缝绣","斋醮","焚香","作灶","治病","针灸","教牛马","探病","扫舍","沐浴","拆卸","动土","破土","修造","起基","安机械","立券","交易","纳财","开光","竖柱","上梁","开仓","出货财","筑堤","造屋","合帐","栽种","牧养","纳畜","缝绣","斋醮","焚香","针灸","探病"};
    private static final String[] CHEN_ZHI_JI = {"开市","安床","出行","移徙","祭祀","嫁娶","动土","破土","安葬","嫁娶","动土","破土"};
    private static final String[] YI_WU_ZHI = {"捕捉","畋猎","嫁娶","纳采","订盟","动土","起基","定磉","开渠","平治道涂","造仓","置产","开市","立券","交易","纳财","开光","竖柱","上梁","开仓","出货财","栽种","纳畜","牧养","缝绣","斋醮","焚香","作灶","造屋","合帐","入宅","移徙","安床","拆卸","动土","破土","修造","起基","安机械","立券","交易","纳财","开光","竖柱","上梁","开仓","出货财","筑堤","造仓","纳畜","缝绣","斋醮","焚香","造庙","造桥","针灸","探病","行丧","取渔","栽种","扫舍","沐浴","治病","扫舍"};
    private static final String[] WU_ZHI_JI = {"开市","安床","出行","移徙","祭祀","祈福","嫁娶","动土","破土","安葬","动土","破土","开渠","动土","破土"};

    /** 获取某日期的完整农历信息 */
    public Map<String, Object> getLunarInfo(long timestampMs) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate date = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate();

        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        LunarData lunar = solarToLunar(year, month, day);
        String ganzhiYear = getGanzhiYear(year);
        String ganzhiMonth = getGanzhiMonth(year, month);
        String ganzhiDay = getGanzhiDay(year, month, day);
        String shengxiao = SHENG_XIAO[(year - 1900) % 12];
        String jieqi = getNearestJieQi(date);
        String[] yiJi = calculateYiJi(lunar.lunarMonth, day, ganzhiDay);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lunarYear", lunar.lunarYear);
        result.put("lunarMonth", lunar.lunarMonthStr);
        result.put("lunarDay", lunar.lunarDayStr);
        result.put("ganZhiYear", ganzhiYear);
        result.put("ganZhiMonth", ganzhiMonth);
        result.put("ganZhiDay", ganzhiDay);
        result.put("shengXiao", shengxiao);
        result.put("jieQi", jieqi);
        result.put("yi", yiJi[0]);
        result.put("ji", yiJi[1]);

        return result;
    }

    /** 核心：公历转农历 */
    private LunarData solarToLunar(int year, int month, int day) {
        int offset = daysFromSolarBase(year, month, day) - daysFromLunarBase(1900);

        int lunarYear = 1900;
        int days = 0;
        while (lunarYear < 2100 && offset > days) {
            days = daysInLunarYear(lunarYear);
            offset -= days;
            lunarYear++;
        }

        int lunarMonth = 1;
        boolean isLeap = false;
        int[] monthDays = getLunarMonthDays(lunarYear);

        while (lunarMonth < 13 && offset > 0) {
            int d = isLeap ? monthDays[12] : monthDays[lunarMonth];
            if (isLeap && lunarMonth == getLeapMonth(lunarYear)) {
                d = monthDays[12];
            }
            offset -= d;
            if (!isLeap && lunarMonth == getLeapMonth(lunarYear)) {
                isLeap = true;
            } else {
                lunarMonth++;
                if (isLeap && lunarMonth - 1 == getLeapMonth(lunarYear)) {
                    isLeap = false;
                }
            }
        }

        int lunarDay = offset + 1;

        String[] monthNames = {"正","二","三","四","五","六","七","八","九","十","冬","腊"};
        String lunarMonthStr = isLeap ? "闰" + monthNames[lunarMonth - 1] + "月" : monthNames[lunarMonth - 1] + "月";

        String[] dayNames = {"初一","初二","初三","初四","初五","初六","初七","初八","初九","初十",
                "十一","十二","十三","十四","十五","十六","十七","十八","十九","二十",
                "廿一","廿二","廿三","廿四","廿五","廿六","廿七","廿八","廿九","三十"};

        String lunarDayStr = lunarDay <= 30 ? dayNames[lunarDay - 1] : "三十";

        return new LunarData(lunarYear, lunarMonth, isLeap, lunarMonthStr, lunarDayStr);
    }

    private int daysFromSolarBase(int year, int month, int day) {
        int days = (year - 1900) * 365 + (year - 1900) / 4;
        int[] mDays = {0,31,59,90,120,151,181,212,243,273,304,334};
        days += mDays[month - 1] + day;
        if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) {
            if (month > 2) days++;
        }
        return days;
    }

    private int daysFromLunarBase(int year) {
        int days = 0;
        for (int y = 1900; y < year; y++) {
            days += daysInLunarYear(y);
        }
        return days;
    }

    private int daysInLunarYear(int year) {
        int info = LUNAR_INFO[year - 1900];
        int days = 12 * 29;
        for (int i = 0; i < 12; i++) {
            days += (info >> i & 1) == 1 ? 30 : 29;
        }
        return days;
    }

    private int[] getLunarMonthDays(int year) {
        int info = LUNAR_INFO[year - 1900];
        int leapMonth = getLeapMonth(year);
        int[] days = new int[13];
        for (int i = 1; i <= 12; i++) {
            int m = (info >> (i - 1)) & 1;
            days[i] = m == 1 ? 30 : 29;
        }
        days[12] = (info >> 15 & 1) == 1 ? days[12] : 0;
        return days;
    }

    private int getLeapMonth(int year) {
        return LUNAR_INFO[year - 1900] >> 12 & 0xF;
    }

    private String getGanzhiYear(int year) {
        int gan = (year - 4) % 10;
        int zhi = (year - 4) % 12;
        return TIAN_GAN[gan] + DI_ZHI[zhi];
    }

    private String getGanzhiMonth(int year, int month) {
        int gan = (year - 4) % 10 * 2 + month % 12 % 2;
        if (gan >= 10) gan = gan % 10;
        int zhi = (month + 2) % 12;
        return TIAN_GAN[gan] + DI_ZHI[zhi] + "月";
    }

    private String getGanzhiDay(int year, int month, int day) {
        int total = (int)daysFromSolarBase(year, month, day) + 10;
        int gan = total % 10;
        int zhi = total % 12;
        return TIAN_GAN[gan] + DI_ZHI[zhi];
    }

    private String getNearestJieQi(LocalDate date) {
        int y = date.getYear();
        for (int i = 0; i < JIE_QI.length; i++) {
            LocalDate jq = LocalDate.of(y, (i / 2) + 1, JIE_QI_DAYS[i][i % 2]);
            if (date.equals(jq)) return JIE_QI[i];
        }
        // 跨年检查
        if (date.getMonthValue() == 12) {
            LocalDate jq = LocalDate.of(y + 1, 1, JIE_QI_DAYS[0][1]);
            if (date.equals(jq)) return JIE_QI[1];
        }
        return "";
    }

    private String[] calculateYiJi(int lunarMonth, int day, String ganzhiDay) {
        // 简化版宜忌，按地支轮流
        String yiPart, jiPart;
        int index = (lunarMonth * day) % 5;
        switch (index) {
            case 0: yiPart = join(JIA_ZHI_YI, 8); jiPart = join(JIA_ZHI_JI, 4); break;
            case 1: yiPart = join(YI_CHEN_ZHI, 8); jiPart = join(CHEN_ZHI_JI, 4); break;
            case 2: yiPart = join(YI_KEYWORDS, 8); jiPart = join(JI_KEYWORDS, 4); break;
            case 3: yiPart = join(YI_WU_ZHI, 8); jiPart = join(WU_ZHI_JI, 4); break;
            default: yiPart = join(YI_KEYWORDS, 8); jiPart = join(JI_KEYWORDS, 4); break;
        }
        return new String[]{yiPart, jiPart};
    }

    private String join(String[] arr, int count) {
        StringBuilder sb = new StringBuilder();
        int max = Math.min(count, arr.length);
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append(" ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private record LunarData(int lunarYear, int lunarMonth, boolean isLeap,
                             String lunarMonthStr, String lunarDayStr) {}
}
