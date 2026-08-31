package com.carelink.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.carelink.app.R;
import com.carelink.app.ui.elder.ElderMainActivity;

/**
 * 摔倒检测服务
 * 使用加速度计和陀螺仪数据进行摔倒检测
 *
 * 检测算法：阈值法 + 自由落体检测 + 冲击检测 + 静止检测
 */
public class FallDetectionService extends Service implements SensorEventListener {

    private static final String TAG = "FallDetectionService";
    private static final String CHANNEL_ID = "fall_detection_channel";

    // ==================== 检测参数 ====================
    // 加速度阈值 (单位: g)
    private static final float FALL_ACCELERATION_THRESHOLD = 3.0f;  // 摔倒冲击阈值
    private static final float FREE_FALL_THRESHOLD = 0.5f;         // 自由落体阈值

    // 陀螺仪阈值 (单位: rad/s)
    private static final float FALL_GYRO_THRESHOLD = 2.5f;          // 摔倒时角速度

    // 时间窗口 (毫秒)
    private static final long IMPACT_WINDOW_MS = 500;               // 冲击检测窗口
    private static final long STILL_DURATION_MS = 10000;            // 静止判定时间（摔倒后是否恢复）
    private static final long DETECTION_COOLDOWN_MS = 60000;        // 检测冷却时间（防止重复报警）

    // ==================== 状态定义 ====================
    private static final int STATE_IDLE = 0;
    private static final int STATE_FREE_FALL = 1;
    private static final int STATE_IMPACT = 2;
    private static final int STATE_FALLEN = 3;
    private static final int STATE_RECOVERED = 4;

    // ==================== 成员变量 ====================
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private int currentState = STATE_IDLE;

    private float[] accelerometerData = new float[3];
    private float[] gyroscopeData = new float[3];

    private long freeFallStartTime = 0;
    private long impactStartTime = 0;
    private long fallenStartTime = 0;
    private long lastDetectionTime = 0;

    private Handler handler;
    private FallDetectionCallback callback;

    // 历史数据（用于平滑）
    private static final int HISTORY_SIZE = 10;
    private float[] accelMagnitudeHistory = new float[HISTORY_SIZE];
    private int historyIndex = 0;

    // ==================== Binder ====================
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public FallDetectionService getService() {
            return FallDetectionService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        initSensors();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1001, createNotification());
        startSensorListening();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSensorListening();
    }

    // ==================== 初始化 ====================
    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    private void startSensorListening() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope,
                    SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void stopSensorListening() {
        sensorManager.unregisterListener(this);
    }

    // ==================== 传感器数据处理 ====================
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerometerData(event.values);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            handleGyroscopeData(event.values);
        }

        // 状态机处理
        processStateMachine();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 不需要处理
    }

    /**
     * 处理加速度计数据
     */
    private void handleAccelerometerData(float[] values) {
        // 复制数据
        accelerometerData[0] = values[0];
        accelerometerData[1] = values[1];
        accelerometerData[2] = values[2];

        // 计算合加速度（去除重力影响）
        float magnitude = calculateMagnitude(values);

        // 添加到历史数据
        accelMagnitudeHistory[historyIndex] = magnitude;
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;

        // 计算平滑值
        float smoothedMagnitude = getSmoothedMagnitude();

        // 更新自由落体状态
        if (smoothedMagnitude < FREE_FALL_THRESHOLD) {
            if (currentState == STATE_IDLE) {
                freeFallStartTime = System.currentTimeMillis();
                currentState = STATE_FREE_FALL;
            }
        }
    }

    /**
     * 处理陀螺仪数据
     */
    private void handleGyroscopeData(float[] values) {
        gyroscopeData[0] = values[0];
        gyroscopeData[1] = values[1];
        gyroscopeData[2] = values[2];
    }

    /**
     * 计算加速度幅值
     */
    private float calculateMagnitude(float[] values) {
        return (float) Math.sqrt(values[0] * values[0]
                + values[1] * values[1]
                + values[2] * values[2]) / SensorManager.GRAVITY_EARTH;
    }

    /**
     * 获取平滑后的加速度幅值
     */
    private float getSmoothedMagnitude() {
        float sum = 0;
        for (float v : accelMagnitudeHistory) {
            sum += v;
        }
        return sum / HISTORY_SIZE;
    }

    // ==================== 状态机 ====================
    private void processStateMachine() {
        long currentTime = System.currentTimeMillis();
        float currentMagnitude = getSmoothedMagnitude();
        float gyroMagnitude = calculateMagnitude(gyroscopeData);

        switch (currentState) {
            case STATE_IDLE:
                // 等待自由落体
                break;

            case STATE_FREE_FALL:
                // 检测到自由落体后，等待冲击
                if (currentMagnitude > FALL_ACCELERATION_THRESHOLD) {
                    impactStartTime = currentTime;
                    currentState = STATE_IMPACT;
                } else if (currentTime - freeFallStartTime > 2000) {
                    // 自由落体时间过长，可能不是摔倒
                    currentState = STATE_IDLE;
                }
                break;

            case STATE_IMPACT:
                // 检测到冲击，检查是否满足摔倒条件
                boolean isHighImpact = currentMagnitude > FALL_ACCELERATION_THRESHOLD;
                boolean hasRotation = gyroMagnitude > FALL_GYRO_THRESHOLD;

                if (isHighImpact && hasRotation) {
                    fallenStartTime = currentTime;
                    currentState = STATE_FALLEN;
                    triggerFallDetection();
                } else if (currentTime - impactStartTime > IMPACT_WINDOW_MS) {
                    // 冲击时间过长，不是摔倒
                    currentState = STATE_IDLE;
                }
                break;

            case STATE_FALLEN:
                // 摔倒后等待恢复或确认
                // 如果检测到正常活动（站立、行走），则判定已恢复
                if (isNormalActivity()) {
                    currentState = STATE_RECOVERED;
                    // 延迟确认，期间若无响应则触发报警
                    scheduleFallConfirmation();
                }

                // 超时未恢复，触发报警
                if (currentTime - fallenStartTime > STILL_DURATION_MS) {
                    if (callback != null) {
                        callback.onFallDetected(getFallSeverity());
                    }
                    currentState = STATE_IDLE;
                    lastDetectionTime = currentTime;
                }
                break;

            case STATE_RECOVERED:
                // 老人自己站起来了，不需要报警
                cancelFallConfirmation();
                currentState = STATE_IDLE;
                break;
        }
    }

    /**
     * 检测是否为正常活动（行走、站立等）
     */
    private boolean isNormalActivity() {
        float accelMagnitude = getSmoothedMagnitude();
        float gyroMagnitude = calculateMagnitude(gyroscopeData);

        // 正常行走：加速度在 0.5-2g 之间，有周期性变化
        boolean isWalking = accelMagnitude > 0.5f && accelMagnitude < 2.0f;
        // 站立/坐着：加速度接近 1g，基本静止
        boolean isStationary = Math.abs(accelMagnitude - 1.0f) < 0.3f;
        // 有明显角速度变化
        boolean hasMovement = gyroMagnitude > 0.5f;

        return isWalking || isStationary || hasMovement;
    }

    /**
     * 触发摔倒检测（初步判定）
     */
    private void triggerFallDetection() {
        if (callback != null) {
            callback.onFallSuspected();
        }
    }

    /**
     * 获取摔倒严重程度
     */
    private int getFallSeverity() {
        float maxImpact = 0;
        for (float v : accelMagnitudeHistory) {
            if (v > maxImpact) maxImpact = v;
        }

        if (maxImpact > 5.0f) {
            return 3; // 严重
        } else if (maxImpact > 4.0f) {
            return 2; // 中等
        } else {
            return 1; // 轻微
        }
    }

    // ==================== 摔倒确认机制 ====================
    private Runnable fallConfirmationRunnable;

    private void scheduleFallConfirmation() {
        cancelFallConfirmation();
        fallConfirmationRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentState == STATE_RECOVERED) {
                    // 老人已恢复，无需报警
                    currentState = STATE_IDLE;
                }
            }
        };
        handler.postDelayed(fallConfirmationRunnable, STILL_DURATION_MS);
    }

    private void cancelFallConfirmation() {
        if (fallConfirmationRunnable != null) {
            handler.removeCallbacks(fallConfirmationRunnable);
            fallConfirmationRunnable = null;
        }
    }

    // ==================== 回调接口 ====================
    public interface FallDetectionCallback {
        void onFallSuspected();      // 初步检测到摔倒
        void onFallDetected(int severity);  // 确认摔倒
        void onFallRecovered();      // 老人自己站起来
    }

    public void setCallback(FallDetectionCallback callback) {
        this.callback = callback;
    }

    // ==================== 通知 ====================
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "摔倒检测服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于后台监测老人是否摔倒");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, ElderMainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CareLink 守护中")
                .setContentText("正在监测您的安全...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // ==================== 公共方法 ====================
    public boolean isRunning() {
        return currentState != STATE_IDLE || accelerometer != null;
    }

    public void start() {
        startSensorListening();
    }

    public void stop() {
        stopSensorListening();
        currentState = STATE_IDLE;
    }
}
