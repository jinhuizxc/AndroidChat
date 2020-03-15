package cn.wildfirechat.client;

/**
 * Created by heavyrainlee on 2018/1/26.
 *
 * 连接状态
 *
 */

public interface ConnectionStatus {
    int ConnectionStatusRejected = -3; // 连接状态被拒绝
    int ConnectionStatusLogout = -2;  // 连接状态注销
    int ConnectionStatusUnconnected = -1;  // 连接状态未连接
    int ConnectionStatusConnecting = 0;  // 连接状态连接中
    int ConnectionStatusConnected = 1;  // 连接状态已连接
    int ConnectionStatusReceiveing = 2;  // 接收连接状态
}
