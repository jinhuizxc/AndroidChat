package cn.wildfirechat.client;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.tencent.mars.BaseEvent;
import com.tencent.mars.Mars;
import com.tencent.mars.app.AppLogic;
import com.tencent.mars.proto.ProtoLogic;
import com.tencent.mars.sdt.SdtLogic;
import com.tencent.mars.xlog.Log;
import com.tencent.mars.xlog.Xlog;
import com.wildfirechat.client.BuildConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import cn.wildfirechat.message.CallStartMessageContent;
import cn.wildfirechat.message.FileMessageContent;
import cn.wildfirechat.message.ImageMessageContent;
import cn.wildfirechat.message.ImageTextMessageContent;
import cn.wildfirechat.message.LocationMessageContent;
import cn.wildfirechat.message.MediaMessageContent;
import cn.wildfirechat.message.Message;
import cn.wildfirechat.message.MessageContent;
import cn.wildfirechat.message.SoundMessageContent;
import cn.wildfirechat.message.StickerMessageContent;
import cn.wildfirechat.message.TextMessageContent;
import cn.wildfirechat.message.TypingMessageContent;
import cn.wildfirechat.message.UnknownMessageContent;
import cn.wildfirechat.message.VideoMessageContent;
import cn.wildfirechat.message.core.ContentTag;
import cn.wildfirechat.message.core.MessageDirection;
import cn.wildfirechat.message.core.MessagePayload;
import cn.wildfirechat.message.core.MessageStatus;
import cn.wildfirechat.message.core.PersistFlag;
import cn.wildfirechat.message.notification.AddGroupMemberNotificationContent;
import cn.wildfirechat.message.notification.ChangeGroupNameNotificationContent;
import cn.wildfirechat.message.notification.ChangeGroupPortraitNotificationContent;
import cn.wildfirechat.message.notification.CreateGroupNotificationContent;
import cn.wildfirechat.message.notification.DismissGroupNotificationContent;
import cn.wildfirechat.message.notification.KickoffGroupMemberNotificationContent;
import cn.wildfirechat.message.notification.ModifyGroupAliasNotificationContent;
import cn.wildfirechat.message.notification.NotificationMessageContent;
import cn.wildfirechat.message.notification.QuitGroupNotificationContent;
import cn.wildfirechat.message.notification.RecallMessageContent;
import cn.wildfirechat.message.notification.TipNotificationContent;
import cn.wildfirechat.message.notification.TransferGroupOwnerNotificationContent;
import cn.wildfirechat.model.ChannelInfo;
import cn.wildfirechat.model.ChatRoomInfo;
import cn.wildfirechat.model.ChatRoomMembersInfo;
import cn.wildfirechat.model.Conversation;
import cn.wildfirechat.model.ConversationInfo;
import cn.wildfirechat.model.ConversationSearchResult;
import cn.wildfirechat.model.FriendRequest;
import cn.wildfirechat.model.GroupInfo;
import cn.wildfirechat.model.GroupMember;
import cn.wildfirechat.model.GroupSearchResult;
import cn.wildfirechat.model.ModifyMyInfoEntry;
import cn.wildfirechat.model.NullUserInfo;
import cn.wildfirechat.model.ProtoChannelInfo;
import cn.wildfirechat.model.ProtoChatRoomInfo;
import cn.wildfirechat.model.ProtoChatRoomMembersInfo;
import cn.wildfirechat.model.ProtoConversationInfo;
import cn.wildfirechat.model.ProtoConversationSearchresult;
import cn.wildfirechat.model.ProtoFriendRequest;
import cn.wildfirechat.model.ProtoGroupInfo;
import cn.wildfirechat.model.ProtoGroupMember;
import cn.wildfirechat.model.ProtoGroupSearchResult;
import cn.wildfirechat.model.ProtoMessage;
import cn.wildfirechat.model.ProtoUserInfo;
import cn.wildfirechat.model.UnreadCount;
import cn.wildfirechat.model.UserInfo;
import cn.wildfirechat.remote.RecoverReceiver;

import static cn.wildfirechat.client.ConnectionStatus.ConnectionStatusConnected;
import static cn.wildfirechat.client.ConnectionStatus.ConnectionStatusLogout;
import static cn.wildfirechat.client.ConnectionStatus.ConnectionStatusUnconnected;
import static cn.wildfirechat.remote.UserSettingScope.ConversationSilent;
import static cn.wildfirechat.remote.UserSettingScope.ConversationTop;
import static com.tencent.mars.comm.PlatformComm.context;


/**
 * Created by heavyrain lee on 2017/11/19.
 *
 * 客户端的服务类
 *
 * 腾讯的mars-core里面的类
 * SdtLogic：
 * AppLogic：
 * ProtoLogic：
 * Xlog：
 * Log：
 */

public class ClientService extends Service implements SdtLogic.ICallBack,
        AppLogic.ICallBack,
        ProtoLogic.IConnectionStatusCallback,
        ProtoLogic.IReceiveMessageCallback,
        ProtoLogic.IUserInfoUpdateCallback,
        ProtoLogic.ISettingUpdateCallback,
        ProtoLogic.IFriendRequestListUpdateCallback,
        ProtoLogic.IFriendListUpdateCallback,
        ProtoLogic.IGroupInfoUpdateCallback,
        ProtoLogic.IChannelInfoUpdateCallback, ProtoLogic.IGroupMembersUpdateCallback {

    // 内容映射器
    private Map<Integer, Class<? extends MessageContent>> contentMapper = new HashMap<>();

    // 连接状态
    private int mConnectionStatus;
    // 备份设备令牌
    private String mBackupDeviceToken;
    // 备份推送类型
    private int mBackupPushType;
    // 是否已登录
    private boolean logined;
    // 用户id
    private String userId;
    // 接受消息的监听器
    private RemoteCallbackList<IOnReceiveMessageListener> onReceiveMessageListeners = new WfcRemoteCallbackList<>();
    // 消息状态改变监听器
    private RemoteCallbackList<IOnConnectionStatusChangeListener> onConnectionStatusChangeListeners = new WfcRemoteCallbackList<>();
    // 好友更新监听器
    private RemoteCallbackList<IOnFriendUpdateListener> onFriendUpdateListenerRemoteCallbackList = new WfcRemoteCallbackList<>();
    // 好友用户信息更新监听器
    private RemoteCallbackList<IOnUserInfoUpdateListener> onUserInfoUpdateListenerRemoteCallbackList = new WfcRemoteCallbackList<>();
    // 群组信息更新远程回调列表
    private RemoteCallbackList<IOnGroupInfoUpdateListener> onGroupInfoUpdateListenerRemoteCallbackList = new WfcRemoteCallbackList<>();
    // 设置更新监听远程回调列表
    private RemoteCallbackList<IOnSettingUpdateListener> onSettingUpdateListenerRemoteCallbackList = new WfcRemoteCallbackList<>();
    // 渠道信息更新监听回调列表
    private RemoteCallbackList<IOnChannelInfoUpdateListener> onChannelInfoUpdateListenerRemoteCallbackList = new WfcRemoteCallbackList<>();
    // 群组成员更新回调列表
    private RemoteCallbackList<IOnGroupMembersUpdateListener> onGroupMembersUpdateListenerRemoteCallbackList = new WfcRemoteCallbackList<>();

    // tencent mars用户信息
    private AppLogic.AccountInfo accountInfo = new AppLogic.AccountInfo();
    //        public final String DEVICE_NAME = android.os.Build.MANUFACTURER + "-" + android.os.Build.MODEL;
    public String DEVICE_TYPE = "Android";//"android-" + android.os.Build.VERSION.SDK_INT;
    // 设备info
    private AppLogic.DeviceInfo info;

    private int clientVersion = 200;
    private static final String TAG = "ClientService";
    // 连接接收器
    private BaseEvent.ConnectionReceiver mConnectionReceiver;

    private class ClientServiceStub extends IRemoteClient.Stub {
        private String mHost;
        private int mPort;

        @Override
        public String getClientId() throws RemoteException {
            return getDeviceType().clientid;
        }

        @Override
        public void connect(String userName, String userPwd) throws RemoteException {
            if (logined) {
                return;
            }

            logined = true;
            accountInfo.userName = userName;
            // 连接状态未连接
            mConnectionStatus = ConnectionStatusUnconnected;
            userId = userName;
            ProtoLogic.setConnectionStatusCallback(ClientService.this);
            ProtoLogic.setReceiveMessageCallback(ClientService.this);

            ProtoLogic.setAuthInfo(userName, userPwd);

            // 动态注册连接接收器
            if (mConnectionReceiver == null) {
                mConnectionReceiver = new BaseEvent.ConnectionReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
                registerReceiver(mConnectionReceiver, filter);
            }

            // mars-core连接域名、端口
            ProtoLogic.connect(mHost, mPort);
        }

        @Override
        public void setOnReceiveMessageListener(IOnReceiveMessageListener listener) throws RemoteException {
            onReceiveMessageListeners.register(listener);
        }

        @Override
        public void setOnConnectionStatusChangeListener(IOnConnectionStatusChangeListener listener) throws RemoteException {
            onConnectionStatusChangeListeners.register(listener);
        }


        @Override
        public void setOnUserInfoUpdateListener(IOnUserInfoUpdateListener listener) throws RemoteException {
            onUserInfoUpdateListenerRemoteCallbackList.register(listener);
        }

        @Override
        public void setOnGroupInfoUpdateListener(IOnGroupInfoUpdateListener listener) throws RemoteException {
            onGroupInfoUpdateListenerRemoteCallbackList.register(listener);
        }

        @Override
        public void setOnFriendUpdateListener(IOnFriendUpdateListener listener) throws RemoteException {
            onFriendUpdateListenerRemoteCallbackList.register(listener);
        }

        @Override
        public void setOnSettingUpdateListener(IOnSettingUpdateListener listener) throws RemoteException {
            onSettingUpdateListenerRemoteCallbackList.register(listener);
        }

        @Override
        public void setOnChannelInfoUpdateListener(IOnChannelInfoUpdateListener listener) throws RemoteException {
            onChannelInfoUpdateListenerRemoteCallbackList.register(listener);
        }

        @Override
        public void setOnGroupMembersUpdateListener(IOnGroupMembersUpdateListener listener) throws RemoteException {
            onGroupMembersUpdateListenerRemoteCallbackList.register(listener);
        }

        @Override
        public void disconnect(boolean clearSession) throws RemoteException {
            logined = false;
            userId = null;
            mConnectionStatus = ConnectionStatusLogout;
            onConnectionStatusChanged(ConnectionStatusLogout);

//            int protoStatus = ProtoLogic.getConnectionStatus();
//            if (mars::stn::getConnectionStatus() != mars::stn::kConnectionStatusConnected && mars::stn::getConnectionStatus() != mars::stn::kConnectionStatusReceiveing) {
//                [self destroyMars];
//            }
            // 注销连接接收器
            if (mConnectionReceiver != null) {
                unregisterReceiver(mConnectionReceiver);
                mConnectionReceiver = null;
            }

            ProtoLogic.setConnectionStatusCallback(null);
            ProtoLogic.setReceiveMessageCallback(null);
            ProtoLogic.disconnect(clearSession ? 8 : 0);

        }

        @Override
        public void setForeground(int isForeground) throws RemoteException {
            // 设置在前台
            BaseEvent.onForeground(isForeground == 1);
        }

        @Override
        public void onNetworkChange() {
            // 网络状态改变
            BaseEvent.onNetworkChange();
        }

        @Override
        public void setServerAddress(String host, int port) throws RemoteException {
            mHost = host;
            mPort = port;
        }

        @Override
        public void registerMessageContent(String msgContentCls) throws RemoteException {
            try {
                // 通过反射获取类
                Class cls = Class.forName(msgContentCls);
                ContentTag tag = (ContentTag) cls.getAnnotation(ContentTag.class);
                if (tag != null) {
                    Class curClazz = contentMapper.get(tag.type());
                    if (curClazz != null && !curClazz.equals(cls)) {
                        throw new IllegalArgumentException("messageContent type duplicate");
                    }
                    contentMapper.put(tag.type(), cls);
                    ProtoLogic.registerMessageFlag(tag.type(), tag.flag().getValue());
                } else {
                    throw new IllegalStateException("ContentTag annotation must be set!");
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        /**
         * TODO 转换消息
         * @param msg
         * @return
         */
        private ProtoMessage convertMessage(Message msg) {
            ProtoMessage protoMessage = new ProtoMessage();

            msg.sender = accountInfo.userName;
            msg.status = MessageStatus.Sending;
            msg.serverTime = System.currentTimeMillis();
            msg.direction = MessageDirection.Send;

            if (msg.conversation != null) {
                protoMessage.setConversationType(msg.conversation.type.ordinal());
                protoMessage.setTarget(msg.conversation.target);
                protoMessage.setLine(msg.conversation.line);
            }
            protoMessage.setFrom(msg.sender);
            protoMessage.setTos(msg.toUsers);
            MessagePayload payload = msg.content.encode();
            payload.contentType = msg.content.getClass().getAnnotation(ContentTag.class).type();
            protoMessage.setContent(payload.toProtoContent());
            protoMessage.setMessageId(msg.messageId);
            protoMessage.setDirection(msg.direction.ordinal());
            protoMessage.setStatus(msg.status.ordinal());
            protoMessage.setMessageUid(msg.messageUid);
            protoMessage.setTimestamp(msg.serverTime);

            return protoMessage;
        }

        /**
         * 发送消息
         * @param msg
         * @param callback
         * @param expireDuration
         * @throws RemoteException
         */
        @Override
        public void send(cn.wildfirechat.message.Message msg, final ISendMessageCallback callback, int expireDuration) throws RemoteException {

            msg.sender = userId;
            ProtoMessage protoMessage = convertMessage(msg);

            ProtoLogic.sendMessage(protoMessage, expireDuration, new ProtoLogic.ISendMessageCallback() {
                @Override
                public void onSuccess(long messageUid, long timestamp) {
                    try {
                        callback.onSuccess(messageUid, timestamp);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    try {
                        callback.onFailure(errorCode);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onPrepared(long messageId, long savedTime) {
                    try {
                        callback.onPrepared(messageId, savedTime);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onProgress(long uploaded, long total) {
                    try {
                        callback.onProgress(uploaded, total);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMediaUploaded(String remoteUrl) {
                    try {
                        callback.onMediaUploaded(remoteUrl);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void recall(long messageUid, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.recallMessage(messageUid, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public long getServerDeltaTime() throws RemoteException {
            return ProtoLogic.getServerDeltaTime();
        }


        private ConversationInfo convertProtoConversationInfo(ProtoConversationInfo protoInfo) {
            if (protoInfo.getTarget() == null || protoInfo.getTarget().length() == 0) {
                return null;
            }
            ConversationInfo info = new ConversationInfo();
            info.conversation = new Conversation(Conversation.ConversationType.values()[protoInfo.getConversationType()], protoInfo.getTarget(), protoInfo.getLine());
            info.lastMessage = convertProtoMessage(protoInfo.getLastMessage());
            info.timestamp = protoInfo.getTimestamp();
            info.draft = protoInfo.getDraft();
            info.unreadCount = new UnreadCount(protoInfo.getUnreadCount());
            info.isTop = protoInfo.isTop();
            info.isSilent = protoInfo.isSilent();
            return info;
        }

        @Override
        public List<ConversationInfo> getConversationList(int[] conversationTypes, int[] lines) throws RemoteException {
            ProtoConversationInfo[] protoConversationInfos = ProtoLogic.getConversations(conversationTypes, lines);
            List<ConversationInfo> out = new ArrayList<>();
            for (ProtoConversationInfo protoConversationInfo : protoConversationInfos) {
                ConversationInfo info = convertProtoConversationInfo(protoConversationInfo);
                if (info != null)
                    out.add(info);
            }
            return out;
        }

        @Override
        public ConversationInfo getConversation(int conversationType, String target, int line) throws RemoteException {
            return convertProtoConversationInfo(ProtoLogic.getConversation(conversationType, target, line));
        }

        @Override
        public List<cn.wildfirechat.message.Message> getMessages(Conversation conversation, long fromIndex, boolean before, int count, String withUser) throws RemoteException {
            ProtoMessage[] protoMessages = ProtoLogic.getMessages(conversation.type.ordinal(), conversation.target, conversation.line, fromIndex, before, count, withUser);
            List<cn.wildfirechat.message.Message> out = new ArrayList<>();
            for (ProtoMessage protoMessage : protoMessages) {
                cn.wildfirechat.message.Message msg = convertProtoMessage(protoMessage);
                if (msg != null) {
                    out.add(msg);
                }
            }
            return out;
        }

        @Override
        public cn.wildfirechat.message.Message getMessage(long messageId) throws RemoteException {
            return convertProtoMessage(ProtoLogic.getMessage(messageId));
        }

        @Override
        public cn.wildfirechat.message.Message getMessageByUid(long messageUid) throws RemoteException {
            return convertProtoMessage(ProtoLogic.getMessageByUid(messageUid));
        }

        /**
         * 插入消息
         * @param message
         * @param notify
         * @return
         * @throws RemoteException
         */
        @Override
        public Message insertMessage(Message message, boolean notify) throws RemoteException {
            ProtoMessage protoMessage = convertMessage(message);
            message.messageId = ProtoLogic.insertMessage(protoMessage);
            return message;
        }

        /**
         * 更新消息
         * @param message
         * @return
         * @throws RemoteException
         */
        @Override
        public boolean updateMessage(cn.wildfirechat.message.Message message) throws RemoteException {
            ProtoMessage protoMessage = convertMessage(message);
            ProtoLogic.updateMessageContent(protoMessage);
            return false;
        }

        /**
         * 获取未读数
         * @param conversationType
         * @param target
         * @param line
         * @return
         * @throws RemoteException
         */
        @Override
        public UnreadCount getUnreadCount(int conversationType, String target, int line) throws RemoteException {
            return new UnreadCount(ProtoLogic.getUnreadCount(conversationType, target, line));
        }

        /**
         * 获取未读计数
         * @param conversationTypes
         * @param lines
         * @return
         * @throws RemoteException
         */
        @Override
        public UnreadCount getUnreadCountEx(int[] conversationTypes, int[] lines) throws RemoteException {
            return new UnreadCount(ProtoLogic.getUnreadCountEx(conversationTypes, lines));
        }

        /**
         * 清空未读状态
         * @param conversationType
         * @param target
         * @param line
         * @throws RemoteException
         */
        @Override
        public void clearUnreadStatus(int conversationType, String target, int line) throws RemoteException {
            ProtoLogic.clearUnreadStatus(conversationType, target, line);
        }

        /**
         * 清空所有未读状态
         * @throws RemoteException
         */
        @Override
        public void clearAllUnreadStatus() throws RemoteException {
            ProtoLogic.clearAllUnreadStatus();
        }

        /**
         * 设置播放媒体消息
         * @param messageId
         */
        @Override
        public void setMediaMessagePlayed(long messageId) {
            try {
                Message message = getMessage(messageId);
                if (message != null || message.direction == MessageDirection.Send || !(message.content instanceof MediaMessageContent)) {
                    return;
                }
                ProtoLogic.setMediaMessagePlayed(messageId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        /**
         * 移除会话
         * @param conversationType
         * @param target
         * @param line
         * @param clearMsg
         * @throws RemoteException
         */
        @Override
        public void removeConversation(int conversationType, String target, int line, boolean clearMsg) throws RemoteException {
            ProtoLogic.removeConversation(conversationType, target, line, clearMsg);
        }

        /**
         * 设置会话置顶
         * @param conversationType
         * @param target
         * @param line
         * @param top
         * @throws RemoteException
         */
        @Override
        public void setConversationTop(int conversationType, String target, int line, boolean top) throws RemoteException {
            setUserSetting(ConversationTop, conversationType + "-" + line + "-" + target, top ? "1" : "0", null);
        }

        /**
         * 设定对话稿
         * @param conversationType
         * @param target
         * @param line
         * @param draft
         * @throws RemoteException
         */
        @Override
        public void setConversationDraft(int conversationType, String target, int line, String draft) throws RemoteException {
            ProtoLogic.setConversationDraft(conversationType, target, line, draft);
        }

        /**
         * 设置对话静音
         * @param conversationType
         * @param target
         * @param line
         * @param silent
         * @throws RemoteException
         */
        @Override
        public void setConversationSilent(int conversationType, String target, int line, boolean silent) throws RemoteException {
            setUserSetting(ConversationSilent, conversationType + "-" + line + "-" + target, silent ? "1" : "0", null);
        }

        /**
         * 查询用户
         * @param keyword
         * @param callback
         * @throws RemoteException
         */
        @Override
        public void searchUser(String keyword, final ISearchUserCallback callback) throws RemoteException {
            ProtoLogic.searchUser(keyword, new ProtoLogic.ISearchUserCallback() {
                @Override
                public void onSuccess(ProtoUserInfo[] userInfos) {
                    List<UserInfo> out = new ArrayList<>();
                    if (userInfos != null) {
                        for (ProtoUserInfo protoUserInfo : userInfos) {
                            out.add(convertProtoUserInfo(protoUserInfo));
                        }
                    }
                    try {
                        callback.onSuccess(out);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    try {
                        callback.onFailure(errorCode);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        /**
         * 是否为我的好友
         * @param userId
         * @return
         * @throws RemoteException
         */
        @Override
        public boolean isMyFriend(String userId) throws RemoteException {
            return ProtoLogic.isMyFriend(userId);
        }

        /**
         * 获取我的好友列表
         * @param refresh
         * @return
         * @throws RemoteException
         */
        @Override
        public List<String> getMyFriendList(boolean refresh) throws RemoteException {
            List<String> out = new ArrayList<>();
            String[] friends = ProtoLogic.getMyFriendList(refresh);
            if (friends != null) {
                for (String friend : friends) {
                    out.add(friend);
                }
            }
            return out;
        }

        /**
         * 被列入黑名单
         * @param userId
         * @return
         * @throws RemoteException
         */
        @Override
        public boolean isBlackListed(String userId) throws RemoteException {
            return ProtoLogic.isBlackListed(userId);
        }

        /**
         * 获取黑名单列表
         * @param refresh
         * @return
         * @throws RemoteException
         */
        @Override
        public List<String> getBlackList(boolean refresh) throws RemoteException {
            List<String> out = new ArrayList<>();
            String[] friends = ProtoLogic.getBlackList(refresh);
            if (friends != null) {
                for (String friend : friends) {
                    out.add(friend);
                }
            }
            return out;
        }

        /**
         * 获取我的好友列表信息
         * @param refresh
         * @return
         * @throws RemoteException
         */
        @Override
        public List<UserInfo> getMyFriendListInfo(boolean refresh) throws RemoteException {
            List<String> users = getMyFriendList(refresh);
            List<UserInfo> userInfos = new ArrayList<>();
            UserInfo userInfo;
            for (String user : users) {
                userInfo = getUserInfo(user, false);
                if (userInfo == null) {
                    userInfo = new UserInfo();
                    userInfo.uid = user;
                }
                userInfos.add(userInfo);
            }
            return userInfos;
        }

        /**
         * 从远程加载好友列表请求
         * @throws RemoteException
         */
        @Override
        public void loadFriendRequestFromRemote() throws RemoteException {
            ProtoLogic.loadFriendRequestFromRemote();
        }

        /**
         * 获取用户设置
         * @param scope
         * @param key
         * @return
         * @throws RemoteException
         */
        @Override
        public String getUserSetting(int scope, String key) throws RemoteException {
            return ProtoLogic.getUserSetting(scope, key);
        }

        // 获取用户设置
        @Override
        public Map<String, String> getUserSettings(int scope) throws RemoteException {
            return ProtoLogic.getUserSettings(scope);
        }

        /**
         * 设置用户设置
         * @param scope
         * @param key
         * @param value
         * @param callback
         * @throws RemoteException
         */
        @Override
        public void setUserSetting(int scope, String key, String value, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.setUserSetting(scope, key, value, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        if (callback != null)
                            callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        if (callback != null) {
                            callback.onFailure(i);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 启动日志
        @Override
        public void startLog() throws RemoteException {
            Xlog.setConsoleLogOpen(true);
        }

        // 停止日志
        @Override
        public void stopLog() throws RemoteException {
            Xlog.setConsoleLogOpen(false);
        }

        // 设置设备令牌
        @Override
        public void setDeviceToken(String token, int pushType) throws RemoteException {
            if (TextUtils.isEmpty(token)) {
                return;
            }
            mBackupDeviceToken = token;
            mBackupPushType = pushType;
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("mars_core_push_type", pushType).commit();
            if (mConnectionStatus != ConnectionStatusConnected) {
                return;
            }

            ProtoLogic.setDeviceToken(getApplicationContext().getPackageName(), token, pushType);
            mBackupDeviceToken = null;
        }

        /**
         * 转换Proto 好友请求
         * @param protoRequest
         * @return
         */
        private FriendRequest convertProtoFriendRequest(ProtoFriendRequest protoRequest) {
            FriendRequest request = new FriendRequest();

            request.direction = protoRequest.getDirection();
            request.target = protoRequest.getTarget();
            request.reason = protoRequest.getReason();
            request.status = protoRequest.getStatus();
            request.readStatus = protoRequest.getReadStatus();
            request.timestamp = protoRequest.getTimestamp();

            return request;
        }

        // 获取好友请求
        @Override
        public List<FriendRequest> getFriendRequest(boolean incomming) throws RemoteException {
            List<FriendRequest> out = new ArrayList<>();
            ProtoFriendRequest[] requests = ProtoLogic.getFriendRequest(incomming);
            if (requests != null) {
                for (ProtoFriendRequest protoFriendRequest : requests) {
                    out.add(convertProtoFriendRequest(protoFriendRequest));
                }
            }
            return out;
        }

        // 清除未读好友请求状态
        @Override
        public void clearUnreadFriendRequestStatus() throws RemoteException {
            ProtoLogic.clearUnreadFriendRequestStatus();
        }

        // 获取未读好友请求状态
        @Override
        public int getUnreadFriendRequestStatus() throws RemoteException {
            return ProtoLogic.getUnreadFriendRequestStatus();
        }

        // 删除朋友
        @Override
        public void removeFriend(String userId, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.removeFriend(userId, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    try {
                        callback.onFailure(errorCode);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 发送朋友请求
        @Override
        public void sendFriendRequest(String userId, String reason, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.sendFriendRequest(userId, reason, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    try {
                        callback.onFailure(errorCode);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 处理朋友请求
        @Override
        public void handleFriendRequest(String userId, boolean accept, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.handleFriendRequest(userId, accept, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    try {
                        callback.onFailure(errorCode);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 设置黑名单
        @Override
        public void setBlackList(String userId, boolean isBlacked, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.setBlackList(userId, isBlacked, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    try {
                        callback.onFailure(errorCode);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 加入聊天室
        @Override
        public void joinChatRoom(String chatRoomId, IGeneralCallback callback) throws RemoteException {
            ProtoLogic.joinChatRoom(chatRoomId, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 退出聊天室
        @Override
        public void quitChatRoom(String chatRoomId, IGeneralCallback callback) throws RemoteException {
            ProtoLogic.quitChatRoom(chatRoomId, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });

        }

        // 获取聊天室信息
        @Override
        public void getChatRoomInfo(String chatRoomId, long updateDt, IGetChatRoomInfoCallback callback) throws RemoteException {
            ProtoLogic.getChatRoomInfo(chatRoomId, updateDt, new ProtoLogic.IGetChatRoomInfoCallback() {

                @Override
                public void onSuccess(ProtoChatRoomInfo protoChatRoomInfo) {
                    try {
                        callback.onSuccess(converProtoChatRoomInfo(protoChatRoomInfo));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 获取聊天室成员信息
        @Override
        public void getChatRoomMembersInfo(String chatRoomId, int maxCount, IGetChatRoomMembersInfoCallback callback) throws RemoteException {
            ProtoLogic.getChatRoomMembersInfo(chatRoomId, maxCount, new ProtoLogic.IGetChatRoomMembersInfoCallback() {
                @Override
                public void onSuccess(ProtoChatRoomMembersInfo protoChatRoomMembersInfo) {
                    try {
                        callback.onSuccess(convertProtoChatRoomMembersInfo(protoChatRoomMembersInfo));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 删除朋友
        @Override
        public void deleteFriend(String userId, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.deleteFriend(userId, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    try {
                        callback.onFailure(errorCode);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 获取组信息
        @Override
        public GroupInfo getGroupInfo(String groupId, boolean refresh) throws RemoteException {
            ProtoGroupInfo protoGroupInfo = ProtoLogic.getGroupInfo(groupId, refresh);
            return convertProtoGroupInfo(protoGroupInfo);
        }

        // 获取用户信息
        @Override
        public UserInfo getUserInfo(String userId, boolean refresh) throws RemoteException {
            return convertProtoUserInfo(ProtoLogic.getUserInfo(userId, refresh));
        }

        // 获取用户信息, 列表
        @Override
        public List<UserInfo> getUserInfos(List<String> userIds) throws RemoteException {
            List<UserInfo> userInfos = new ArrayList<>();
            for (String userId : userIds) {
                ProtoUserInfo protoUserInfo = ProtoLogic.getUserInfo(userId, false);
                if (protoUserInfo == null) {
                    userInfos.add(new NullUserInfo(userId));
                } else {
                    userInfos.add(convertProtoUserInfo(protoUserInfo));
                }
            }
            return userInfos;
        }

        // 上传媒体
        @Override
        public void uploadMedia(byte[] data, int mediaType, final IUploadMediaCallback callback) throws RemoteException {
            ProtoLogic.uploadMedia(data, mediaType, new ProtoLogic.IUploadMediaCallback() {
                @Override
                public void onSuccess(String s) {
                    try {
                        callback.onSuccess(s);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onProgress(long uploaded, long total) {

                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 修改我的信息
        @Override
        public void modifyMyInfo(List<ModifyMyInfoEntry> values, final IGeneralCallback callback) throws RemoteException {
            Map<Integer, String> protoValues = new HashMap<>();
            for (ModifyMyInfoEntry entry : values
            ) {
                protoValues.put(entry.type.getValue(), entry.value);
            }
            ProtoLogic.modifyMyInfo(protoValues, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 删除留言
        @Override
        public boolean deleteMessage(long messageId) throws RemoteException {
            return ProtoLogic.deleteMessage(messageId);
        }

        // 搜索对话
        @Override
        public List<ConversationSearchResult> searchConversation(String keyword, int[] conversationTypes, int[] lines) throws RemoteException {
            ProtoConversationSearchresult[] protoResults = ProtoLogic.searchConversation(keyword, conversationTypes, lines);
            List<ConversationSearchResult> output = new ArrayList<>();
            if (protoResults != null) {
                for (ProtoConversationSearchresult protoResult : protoResults
                ) {
                    ConversationSearchResult result = new ConversationSearchResult();
                    result.conversation = new Conversation(Conversation.ConversationType.type(protoResult.getConversationType()), protoResult.getTarget(), protoResult.getLine());
                    result.marchedMessage = convertProtoMessage(protoResult.getMarchedMessage());
                    result.timestamp = protoResult.getTimestamp();
                    result.marchedCount = protoResult.getMarchedCount();

                    if (result.marchedMessage != null) {
                        output.add(result);
                    }

                }
            }

            return output;
        }

        // 搜索留言
        @Override
        public List<cn.wildfirechat.message.Message> searchMessage(Conversation conversation, String keyword) throws RemoteException {
            ProtoMessage[] protoMessages = ProtoLogic.searchMessage(conversation.type.getValue(), conversation.target, conversation.line, keyword);
            List<cn.wildfirechat.message.Message> out = new ArrayList<>();

            if (protoMessages != null) {
                for (ProtoMessage protoMsg : protoMessages) {
                    Message msg = convertProtoMessage(protoMsg);
                    if (msg != null) {
                        out.add(convertProtoMessage(protoMsg));
                    }
                }
            }

            return out;
        }


        // 搜索组
        @Override
        public List<GroupSearchResult> searchGroups(String keyword) throws RemoteException {
            ProtoGroupSearchResult[] protoResults = ProtoLogic.searchGroups(keyword);
            List<GroupSearchResult> output = new ArrayList<>();
            if (protoResults != null) {
                for (ProtoGroupSearchResult protoResult : protoResults
                ) {
                    GroupSearchResult result = new GroupSearchResult();
                    result.groupInfo = convertProtoGroupInfo(protoResult.getGroupInfo());
                    result.marchedType = protoResult.getMarchType();
                    result.marchedMembers = new ArrayList<String>(Arrays.asList(protoResult.getMarchedMembers()));
                    output.add(result);
                }
            }

            return output;
        }

        // 搜索好友
        @Override
        public List<UserInfo> searchFriends(String keyworkd) throws RemoteException {
            ProtoUserInfo[] protoUserInfos = ProtoLogic.searchFriends(keyworkd);
            List<UserInfo> out = new ArrayList<>();
            if (protoUserInfos != null) {
                for (ProtoUserInfo protoUserInfo : protoUserInfos) {
                    out.add(convertProtoUserInfo(protoUserInfo));
                }
            }
            return out;
        }

        // 创建群组
        @Override
        public void createGroup(String groupId, String groupName, String groupPortrait, List<String> memberIds, int[] notifyLines, MessagePayload notifyMsg, final IGeneralCallback2 callback) throws RemoteException {
            String[] memberArray = new String[memberIds.size()];
            for (int i = 0; i < memberIds.size(); i++) {
                memberArray[i] = memberIds.get(i);
            }
            ProtoLogic.createGroup(groupId, groupName, groupPortrait, memberArray, notifyLines, notifyMsg.toProtoContent(), new ProtoLogic.IGeneralCallback2() {
                @Override
                public void onSuccess(String s) {
                    try {
                        callback.onSuccess(s);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 添加组成员
        @Override
        public void addGroupMembers(String groupId, List<String> memberIds, int[] notifyLines, MessagePayload notifyMsg, final IGeneralCallback callback) throws RemoteException {
            String[] memberArray = new String[memberIds.size()];
            for (int i = 0; i < memberIds.size(); i++) {
                memberArray[i] = memberIds.get(i);
            }
            ProtoLogic.addMembers(groupId, memberArray, notifyLines, notifyMsg.toProtoContent(), new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 删除组成员
        @Override
        public void removeGroupMembers(String groupId, List<String> memberIds, int[] notifyLines, MessagePayload notifyMsg, final IGeneralCallback callback) throws RemoteException {
            String[] memberArray = new String[memberIds.size()];
            for (int i = 0; i < memberIds.size(); i++) {
                memberArray[i] = memberIds.get(i);
            }
            ProtoLogic.kickoffMembers(groupId, memberArray, notifyLines, notifyMsg.toProtoContent(), new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 退出组
        @Override
        public void quitGroup(String groupId, int[] notifyLines, MessagePayload notifyMsg, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.quitGroup(groupId, notifyLines, notifyMsg.toProtoContent(), new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 解雇小组
        @Override
        public void dismissGroup(String groupId, int[] notifyLines, MessagePayload notifyMsg, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.dismissGroup(groupId, notifyLines, notifyMsg.toProtoContent(), new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 修改组信息
        @Override
        public void modifyGroupInfo(String groupId, int modifyType, String newValue, int[] notifyLines, MessagePayload notifyMsg, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.modifyGroupInfo(groupId, modifyType, newValue, notifyLines, notifyMsg == null ? null : notifyMsg.toProtoContent(), new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 修改组别名
        @Override
        public void modifyGroupAlias(String groupId, String newAlias, int[] notifyLines, MessagePayload notifyMsg, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.modifyGroupAlias(groupId, newAlias, notifyLines, notifyMsg.toProtoContent(), new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 获取小组成员 列表
        @Override
        public List<GroupMember> getGroupMembers(String groupId, boolean forceUpdate) throws RemoteException {
            ProtoGroupMember[] protoGroupMembers = ProtoLogic.getGroupMembers(groupId, forceUpdate);
            List<GroupMember> out = new ArrayList<>();
            for (ProtoGroupMember protoMember : protoGroupMembers) {
                GroupMember member = new GroupMember();
                member.groupId = groupId;
                member.memberId = protoMember.getMemberId();
                member.alias = protoMember.getAlias();
                member.type = GroupMember.GroupMemberType.type(protoMember.getType());
                member.updateDt = protoMember.getUpdateDt();

                out.add(member);
            }
            return out;
        }

        // 获取组成员
        @Override
        public GroupMember getGroupMember(String groupId, String memberId) throws RemoteException {
            ProtoGroupMember protoGroupMember = ProtoLogic.getGroupMember(groupId, memberId);
            return covertProtoGroupMember(protoGroupMember);
        }

        // 转让群组
        @Override
        public void transferGroup(String groupId, String newOwner, int[] notifyLines, MessagePayload notifyMsg, final IGeneralCallback callback) throws RemoteException {
            ProtoLogic.transferGroup(groupId, newOwner, notifyLines, notifyMsg.toProtoContent(), new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }


        // 建立频道
        @Override
        public void createChannel(String channelId, String channelName, String channelPortrait, String desc, String extra, ICreateChannelCallback callback) throws RemoteException {
            ProtoLogic.createChannel(channelId, channelName, channelPortrait, 0, desc, extra, new ProtoLogic.ICreateChannelCallback() {
                @Override
                public void onSuccess(ProtoChannelInfo protoChannelInfo) {
                    try {
                        callback.onSuccess(converProtoChannelInfo(protoChannelInfo));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 修改频道信息
        @Override
        public void modifyChannelInfo(String channelId, int modifyType, String newValue, IGeneralCallback callback) throws RemoteException {

        }

        // 获取频道信息
        @Override
        public ChannelInfo getChannelInfo(String channelId, boolean refresh) throws RemoteException {
            return converProtoChannelInfo(ProtoLogic.getChannelInfo(channelId, refresh));
        }

        // 搜索频道
        @Override
        public void searchChannel(String keyword, ISearchChannelCallback callback) throws RemoteException {
            ProtoLogic.searchChannel(keyword, new ProtoLogic.ISearchChannelCallback() {
                @Override
                public void onSuccess(ProtoChannelInfo[] protoChannelInfos) {
                    List<ChannelInfo> out = new ArrayList<>();
                    if (protoChannelInfos != null) {
                        for (ProtoChannelInfo protoChannelInfo : protoChannelInfos) {
                            out.add(converProtoChannelInfo(protoChannelInfo));
                        }
                    }
                    try {
                        callback.onSuccess(out);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 收听频道
        @Override
        public boolean isListenedChannel(String channelId) throws RemoteException {
            return ProtoLogic.isListenedChannel(channelId);
        }

        // 听频道
        @Override
        public void listenChannel(String channelId, boolean listen, IGeneralCallback callback) throws RemoteException {
            ProtoLogic.listenChannel(channelId, listen, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 销毁频道
        @Override
        public void destoryChannel(String channelId, IGeneralCallback callback) throws RemoteException {
            ProtoLogic.destoryChannel(channelId, new ProtoLogic.IGeneralCallback() {
                @Override
                public void onSuccess() {
                    try {
                        callback.onSuccess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int i) {
                    try {
                        callback.onFailure(i);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // 获取我的频道
        @Override
        public List<String> getMyChannels() throws RemoteException {
            List<String> out = new ArrayList<>();
            String[] channels = ProtoLogic.getMyChannels();
            if (channels != null) {
                for (String channelId : channels) {
                    out.add(channelId);
                }
            }
            return out;
        }

        // 获取收听的频道
        @Override
        public List<String> getListenedChannels() throws RemoteException {
            List<String> out = new ArrayList<>();
            String[] channels = ProtoLogic.getListenedChannels();
            if (channels != null) {
                for (String channelId : channels) {
                    out.add(channelId);
                }
            }
            return out;
        }

    }

    // 转换原始频道信息
    private ChannelInfo converProtoChannelInfo(ProtoChannelInfo protoChannelInfo) {
        if (protoChannelInfo == null) {
            return null;
        }
        ChannelInfo channelInfo = new ChannelInfo();
        channelInfo.channelId = protoChannelInfo.getChannelId();
        channelInfo.name = protoChannelInfo.getName();
        channelInfo.desc = protoChannelInfo.getDesc();
        channelInfo.portrait = protoChannelInfo.getPortrait();
        channelInfo.extra = protoChannelInfo.getExtra();
        channelInfo.owner = protoChannelInfo.getOwner();
        channelInfo.status = ChannelInfo.ChannelStatus.status(protoChannelInfo.getStatus());
        channelInfo.updateDt = protoChannelInfo.getUpdateDt();

        return channelInfo;
    }

    // 转换Proto群组信息
    private GroupInfo convertProtoGroupInfo(ProtoGroupInfo protoGroupInfo) {
        if (protoGroupInfo == null) {
            return null;
        }
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.target = protoGroupInfo.getTarget();
        groupInfo.name = protoGroupInfo.getName();
        groupInfo.portrait = protoGroupInfo.getPortrait();
        groupInfo.owner = protoGroupInfo.getOwner();
        groupInfo.type = GroupInfo.GroupType.type(protoGroupInfo.getType());
        groupInfo.memberCount = protoGroupInfo.getMemberCount();
        groupInfo.extra = protoGroupInfo.getExtra();
        groupInfo.updateDt = protoGroupInfo.getUpdateDt();
        return groupInfo;
    }

    // 隐秘原型组成员
    private GroupMember covertProtoGroupMember(ProtoGroupMember protoGroupMember) {
        if (protoGroupMember == null) {
            return null;
        }
        GroupMember member = new GroupMember();
        member.groupId = protoGroupMember.getGroupId();
        member.memberId = protoGroupMember.getMemberId();
        member.alias = protoGroupMember.getAlias();
        member.type = GroupMember.GroupMemberType.type(protoGroupMember.getType());
        member.updateDt = protoGroupMember.getUpdateDt();
        return member;

    }

    private ChatRoomInfo converProtoChatRoomInfo(ProtoChatRoomInfo protoChatRoomInfo) {
        if (protoChatRoomInfo == null) {
            return null;
        }
        ChatRoomInfo chatRoomInfo = new ChatRoomInfo();
        chatRoomInfo.chatRoomId = protoChatRoomInfo.getChatRoomId();
        chatRoomInfo.title = protoChatRoomInfo.getTitle();
        chatRoomInfo.desc = protoChatRoomInfo.getDesc();
        chatRoomInfo.portrait = protoChatRoomInfo.getPortrait();
        chatRoomInfo.extra = protoChatRoomInfo.getExtra();
        chatRoomInfo.state = ChatRoomInfo.State.values()[protoChatRoomInfo.getState()];
        chatRoomInfo.memberCount = protoChatRoomInfo.getMemberCount();
        chatRoomInfo.createDt = protoChatRoomInfo.getCreateDt();
        chatRoomInfo.updateDt = protoChatRoomInfo.getUpdateDt();

        return chatRoomInfo;
    }

    private ChatRoomMembersInfo convertProtoChatRoomMembersInfo(ProtoChatRoomMembersInfo protoChatRoomMembersInfo) {
        //public int memberCount;
        //public List<String> members;
        if (protoChatRoomMembersInfo == null) {
            return null;
        }
        ChatRoomMembersInfo chatRoomMembersInfo = new ChatRoomMembersInfo();
        chatRoomMembersInfo.memberCount = protoChatRoomMembersInfo.getMemberCount();
        chatRoomMembersInfo.members = protoChatRoomMembersInfo.getMembers();
        return chatRoomMembersInfo;
    }


    private UserInfo convertProtoUserInfo(ProtoUserInfo protoUserInfo) {
        if (protoUserInfo == null) {
            return null;
        }
        UserInfo userInfo = new UserInfo();
        userInfo.uid = protoUserInfo.getUid();
        userInfo.name = protoUserInfo.getName();
        userInfo.displayName = protoUserInfo.getDisplayName();
        userInfo.portrait = protoUserInfo.getPortrait();
        userInfo.gender = protoUserInfo.getGender();
        userInfo.mobile = protoUserInfo.getMobile();
        userInfo.email = protoUserInfo.getEmail();
        userInfo.address = protoUserInfo.getAddress();
        userInfo.company = protoUserInfo.getCompany();
        userInfo.social = protoUserInfo.getSocial();
        userInfo.extra = protoUserInfo.getExtra();
        userInfo.updateDt = protoUserInfo.getUpdateDt();
        userInfo.type = protoUserInfo.getType();
        return userInfo;
    }

    private MessageContent contentOfType(int type) {
        Class<? extends MessageContent> cls = contentMapper.get(type);
        if (cls != null) {
            try {
                return cls.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return new UnknownMessageContent();
    }

    private final ClientServiceStub mBinder = new ClientServiceStub();

    private List<cn.wildfirechat.message.Message> convertProtoMessages(List<ProtoMessage> protoMessages) {
        List<cn.wildfirechat.message.Message> out = new ArrayList<>();
        for (ProtoMessage protoMessage : protoMessages) {
            cn.wildfirechat.message.Message msg = convertProtoMessage(protoMessage);
            if (msg != null && msg.content != null) {
                out.add(msg);
            }
        }
        return out;
    }

    private cn.wildfirechat.message.Message convertProtoMessage(ProtoMessage protoMessage) {
        if (protoMessage == null || TextUtils.isEmpty(protoMessage.getTarget())) {
            return null;
        }
        cn.wildfirechat.message.Message msg = new cn.wildfirechat.message.Message();
        msg.messageId = protoMessage.getMessageId();
        msg.conversation = new Conversation(Conversation.ConversationType.values()[protoMessage.getConversationType()], protoMessage.getTarget(), protoMessage.getLine());
        msg.sender = protoMessage.getFrom();
        msg.toUsers = protoMessage.getTos();

        msg.content = contentOfType(protoMessage.getContent().getType());
        MessagePayload payload = new MessagePayload(protoMessage.getContent());
        try {
            msg.content.decode(payload);
            if (msg.content instanceof NotificationMessageContent) {
                if (msg.sender.equals(userId)) {
                    ((NotificationMessageContent) msg.content).fromSelf = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (msg.content.getPersistFlag() == PersistFlag.Persist || msg.content.getPersistFlag() == PersistFlag.Persist_And_Count) {
                msg.content = new UnknownMessageContent();
                ((UnknownMessageContent) msg.content).setOrignalPayload(payload);
            } else {
                return null;
            }
        }

        msg.direction = MessageDirection.values()[protoMessage.getDirection()];
        msg.status = MessageStatus.values()[protoMessage.getStatus()];
        msg.messageUid = protoMessage.getMessageUid();
        msg.serverTime = protoMessage.getTimestamp();

        return msg;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    @Override
    public boolean onUnbind(Intent intent) {
        Log.e("ddd", "unbinded");
        return super.onUnbind(intent);
    }

    /**
     * 创建服务
     */
    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化协议
        initProto();
        try {
            mBinder.registerMessageContent(AddGroupMemberNotificationContent.class.getName());
            mBinder.registerMessageContent(CallStartMessageContent.class.getName());
            mBinder.registerMessageContent(ChangeGroupNameNotificationContent.class.getName());
            mBinder.registerMessageContent(ChangeGroupPortraitNotificationContent.class.getName());
            mBinder.registerMessageContent(CreateGroupNotificationContent.class.getName());
            mBinder.registerMessageContent(DismissGroupNotificationContent.class.getName());
            mBinder.registerMessageContent(FileMessageContent.class.getName());
            mBinder.registerMessageContent(ImageMessageContent.class.getName());
            mBinder.registerMessageContent(ImageTextMessageContent.class.getName());
            mBinder.registerMessageContent(KickoffGroupMemberNotificationContent.class.getName());
            mBinder.registerMessageContent(LocationMessageContent.class.getName());
            mBinder.registerMessageContent(ModifyGroupAliasNotificationContent.class.getName());
            mBinder.registerMessageContent(QuitGroupNotificationContent.class.getName());
            mBinder.registerMessageContent(RecallMessageContent.class.getName());
            mBinder.registerMessageContent(SoundMessageContent.class.getName());
            mBinder.registerMessageContent(StickerMessageContent.class.getName());
            mBinder.registerMessageContent(TextMessageContent.class.getName());
            mBinder.registerMessageContent(TipNotificationContent.class.getName());
            mBinder.registerMessageContent(TransferGroupOwnerNotificationContent.class.getName());
            mBinder.registerMessageContent(VideoMessageContent.class.getName());
            mBinder.registerMessageContent(TypingMessageContent.class.getName());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        Log.appenderClose();
        super.onDestroy();
        resetProto();
    }

    private void initProto() {
        AppLogic.setCallBack(this);
        SdtLogic.setCallBack(this);

        // Initialize the Mars PlatformComm
        Mars.init(getApplicationContext(), new Handler(Looper.getMainLooper()));
        Mars.onCreate(true);
        // 记录log信息
        openXlog();

        // 这里把连接状态注销
        mConnectionStatus = ConnectionStatusLogout;

        ProtoLogic.setUserInfoUpdateCallback(this);
        ProtoLogic.setSettingUpdateCallback(this);
        ProtoLogic.setFriendListUpdateCallback(this);
        ProtoLogic.setGroupInfoUpdateCallback(this);
        ProtoLogic.setChannelInfoUpdateCallback(this);
        ProtoLogic.setGroupMembersUpdateCallback(this);
        ProtoLogic.setFriendRequestListUpdateCallback(this);
    }

    /**
     * 服务onDestroy的时候
     * 重置协议
     */
    private void resetProto() {
        Mars.onDestroy();
        AppLogic.setCallBack(null);
        SdtLogic.setCallBack(null);
        ProtoLogic.setUserInfoUpdateCallback(null);
        ProtoLogic.setSettingUpdateCallback(null);
        ProtoLogic.setFriendListUpdateCallback(null);
        ProtoLogic.setGroupInfoUpdateCallback(null);
        ProtoLogic.setChannelInfoUpdateCallback(null);
        ProtoLogic.setFriendRequestListUpdateCallback(null);

        ProtoLogic.setConnectionStatusCallback(null);
        ProtoLogic.setReceiveMessageCallback(null);
    }

    public void openXlog() {

//        int pid = android.os.Process.myPid();
        String processName = getApplicationInfo().packageName;
//        ActivityManager am = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
//        for (ActivityManager.RunningAppProcessInfo appProcess : am.getRunningAppProcesses()) {
//            if (appProcess.pid == pid) {
//                processName = appProcess.processName;
//                break;
//            }
//        }

        if (processName == null) {
            return;
        }

        final String SDCARD;
        if (checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
            SDCARD = getCacheDir().getAbsolutePath();
        } else {
            SDCARD = Environment.getExternalStorageDirectory().getAbsolutePath();
        }

        final String logPath = SDCARD + "/marscore/log";
        final String logCache = SDCARD + "/marscore/cache";

        String logFileName = processName.indexOf(":") == -1 ? "MarsSample" : ("MarsSample_" + processName.substring(processName.indexOf(":") + 1));

        if (BuildConfig.DEBUG) {
            Xlog.appenderOpen(Xlog.LEVEL_VERBOSE, Xlog.AppednerModeAsync, logCache, logPath, logFileName, "");
            Xlog.setConsoleLogOpen(true);
        } else {
            Xlog.appenderOpen(Xlog.LEVEL_INFO, Xlog.AppednerModeAsync, logCache, logPath, logFileName, "");
            Xlog.setConsoleLogOpen(false);
        }
        Log.setLogImp(new Xlog());
    }

    private class WfcRemoteCallbackList<E extends IInterface> extends RemoteCallbackList<E> {
        @Override
        public void onCallbackDied(E callback, Object cookie) {
            Log.e("ClientService", "main process died");
            Intent intent = new Intent(ClientService.this, RecoverReceiver.class);
            sendBroadcast(intent);
        }
    }

    @Override
    public void reportSignalDetectResults(String resultsJson) {

    }

    @Override
    public String getAppFilePath() {
        try {
            File file = new File(ClientService.this.getFilesDir().getAbsolutePath() + "/" + accountInfo.userName);
            if (!file.exists()) {
                file.mkdir();
            }
            return file.toString();
        } catch (Exception e) {
            Log.e("ddd", "", e);
        }

        return null;
    }

    @Override
    public AppLogic.AccountInfo getAccountInfo() {
        return accountInfo;
    }

    @Override
    public int getClientVersion() {
        return 0;
    }

    @Override
    public AppLogic.DeviceInfo getDeviceType() {
        if (info == null) {
            String imei = PreferenceManager.getDefaultSharedPreferences(context).getString("mars_core_uid", "");
            if (TextUtils.isEmpty(imei)) {
                imei = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                if (TextUtils.isEmpty(imei)) {
                    imei = UUID.randomUUID().toString();
                }
                imei += System.currentTimeMillis();
                PreferenceManager.getDefaultSharedPreferences(context).edit().putString("mars_core_uid", imei).apply();
            }
            info = new AppLogic.DeviceInfo(imei);
            info.packagename = context.getPackageName();
            // TODO 自行处理吧，这些信息不是必须的
            info.carriername = "CMCC";
            info.device = "小米6";
            info.deviceversion = "Android8.0";
            info.language = "ZH_CN";
            info.phonename = "XXXx的小米6";
        }
        return info;
    }

    @Override
    public void onConnectionStatusChanged(int status) {
        android.util.Log.d("", "status changed :" + status);
        mConnectionStatus = status;
        if (status == -4) {
            status = -1;
        }
        int i = onConnectionStatusChangeListeners.beginBroadcast();
        IOnConnectionStatusChangeListener listener;
        while (i > 0) {
            i--;
            listener = onConnectionStatusChangeListeners.getBroadcastItem(i);
            try {
                listener.onConnectionStatusChange(status);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        // 结束广播
        onConnectionStatusChangeListeners.finishBroadcast();

        if (mConnectionStatus == ConnectionStatusConnected && !TextUtils.isEmpty(mBackupDeviceToken)) {
            try {
                ProtoLogic.setDeviceToken(getApplicationContext().getPackageName(), mBackupDeviceToken, mBackupPushType);
                mBackupDeviceToken = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRecallMessage(long messageUid) {
        int receiverCount = onReceiveMessageListeners.beginBroadcast();
        IOnReceiveMessageListener listener;
        while (receiverCount > 0) {
            receiverCount--;
            listener = onReceiveMessageListeners.getBroadcastItem(receiverCount);
            try {
                listener.onRecall(messageUid);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        onReceiveMessageListeners.finishBroadcast();
    }

    @Override
    public void onReceiveMessage(List<ProtoMessage> messages, boolean hasMore) {
        if (messages.isEmpty()) {
            return;
        }

        android.util.Log.d("", "RECEIVE MESSAGES");
        List<cn.wildfirechat.message.Message> messageList = convertProtoMessages(messages);
        while (messageList.size() > 0) {
            ArrayList<cn.wildfirechat.message.Message> tmpList;
            if (messageList.size() >= 100) {
                hasMore = true;
                tmpList = new ArrayList<>(messageList.subList(0, 100));
                messageList = new ArrayList<>(messageList.subList(100, messageList.size()));
            } else {
                tmpList = new ArrayList<>(messageList);
                messageList.clear();
            }
            int receiverCount = onReceiveMessageListeners.beginBroadcast();
            IOnReceiveMessageListener listener;
            while (receiverCount > 0) {
                receiverCount--;
                listener = onReceiveMessageListeners.getBroadcastItem(receiverCount);
                try {
                    listener.onReceive(tmpList, hasMore);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            onReceiveMessageListeners.finishBroadcast();
        }
    }

    @Override
    public void onFriendListUpdated() {
        int i = onFriendUpdateListenerRemoteCallbackList.beginBroadcast();
        IOnFriendUpdateListener listener;
        while (i > 0) {
            i--;
            listener = onFriendUpdateListenerRemoteCallbackList.getBroadcastItem(i);
            try {
                listener.onFriendListUpdated();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        onFriendUpdateListenerRemoteCallbackList.finishBroadcast();
    }

    @Override
    public void onFriendRequestUpdated() {
        int i = onFriendUpdateListenerRemoteCallbackList.beginBroadcast();
        IOnFriendUpdateListener listener;
        while (i > 0) {
            i--;
            listener = onFriendUpdateListenerRemoteCallbackList.getBroadcastItem(i);
            try {
                listener.onFriendRequestUpdated();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        onFriendUpdateListenerRemoteCallbackList.finishBroadcast();
    }

    @Override
    public void onGroupInfoUpdated(List<ProtoGroupInfo> list) {
        ArrayList<GroupInfo> groups = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            GroupInfo gi = convertProtoGroupInfo(list.get(i));
            if (gi != null) {
                groups.add(gi);
            }
        }
        int i = onGroupInfoUpdateListenerRemoteCallbackList.beginBroadcast();
        IOnGroupInfoUpdateListener listener;
        while (i > 0) {
            i--;
            listener = onGroupInfoUpdateListenerRemoteCallbackList.getBroadcastItem(i);
            try {
                listener.onGroupInfoUpdated(groups);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        onGroupInfoUpdateListenerRemoteCallbackList.finishBroadcast();
    }

    @Override
    public void onGroupMembersUpdated(String groupId, List<ProtoGroupMember> members) {
        ArrayList<GroupMember> groupMembers = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            GroupMember gm = covertProtoGroupMember(members.get(i));
            if (gm != null) {
                groupMembers.add(gm);
            }
        }
        int i = onGroupMembersUpdateListenerRemoteCallbackList.beginBroadcast();
        IOnGroupMembersUpdateListener listener;
        while (i > 0) {
            i--;
            listener = onGroupMembersUpdateListenerRemoteCallbackList.getBroadcastItem(i);
            try {
                listener.onGroupMembersUpdated(groupId, groupMembers);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        onGroupMembersUpdateListenerRemoteCallbackList.finishBroadcast();
    }


    @Override
    public void onChannelInfoUpdated(List<ProtoChannelInfo> list) {
        ArrayList<ChannelInfo> channels = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ChannelInfo gi = converProtoChannelInfo(list.get(i));
            if (gi != null) {
                channels.add(gi);
            }
        }
        int i = onChannelInfoUpdateListenerRemoteCallbackList.beginBroadcast();
        IOnChannelInfoUpdateListener listener;
        while (i > 0) {
            i--;
            listener = onChannelInfoUpdateListenerRemoteCallbackList.getBroadcastItem(i);
            try {
                listener.onChannelInfoUpdated(channels);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        onChannelInfoUpdateListenerRemoteCallbackList.finishBroadcast();
    }

    // 参数里面直接带上scope, key, value
    @Override
    public void onSettingUpdated() {
        int i = onSettingUpdateListenerRemoteCallbackList.beginBroadcast();
        IOnSettingUpdateListener listener;
        while (i > 0) {
            i--;
            listener = onSettingUpdateListenerRemoteCallbackList.getBroadcastItem(i);
            try {
                listener.onSettingUpdated();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        onSettingUpdateListenerRemoteCallbackList.finishBroadcast();
    }

    @Override
    public void onUserInfoUpdated(List<ProtoUserInfo> list) {
        ArrayList<UserInfo> users = new ArrayList<>();
        for (int j = 0; j < list.size(); j++) {
            UserInfo userInfo = convertProtoUserInfo(list.get(j));
            if (userInfo != null) {
                users.add(userInfo);
            }
        }
        int i = onUserInfoUpdateListenerRemoteCallbackList.beginBroadcast();
        IOnUserInfoUpdateListener listener;
        while (i > 0) {
            i--;
            listener = onUserInfoUpdateListenerRemoteCallbackList.getBroadcastItem(i);
            try {
                listener.onUserInfoUpdated(users);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        onUserInfoUpdateListenerRemoteCallbackList.finishBroadcast();
    }
}
