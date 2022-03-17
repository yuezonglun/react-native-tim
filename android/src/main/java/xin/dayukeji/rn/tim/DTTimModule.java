package xin.dayukeji.rn.tim;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import com.tencent.imsdk.TIMCallBack;
import com.tencent.imsdk.TIMConnListener;
import com.tencent.imsdk.TIMConversation;
import com.tencent.imsdk.TIMConversationType;
import com.tencent.imsdk.TIMElem;
import com.tencent.imsdk.TIMFriendshipManager;
import com.tencent.imsdk.TIMGroupManager;
import com.tencent.imsdk.TIMGroupSystemElem;
import com.tencent.imsdk.TIMLogLevel;
import com.tencent.imsdk.TIMManager;
import com.tencent.imsdk.TIMMessage;
import com.tencent.imsdk.TIMMessageListener;
import com.tencent.imsdk.TIMProfileSystemElem;
import com.tencent.imsdk.TIMProfileSystemType;
import com.tencent.imsdk.TIMSdkConfig;
import com.tencent.imsdk.TIMTextElem;
import com.tencent.imsdk.TIMUserConfig;
import com.tencent.imsdk.TIMUserProfile;
import com.tencent.imsdk.TIMUserStatusListener;
import com.tencent.imsdk.TIMValueCallBack;
import com.tencent.imsdk.TIMCustomElem;
import com.tencent.imsdk.log.QLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DTTimModule extends ReactContextBaseJavaModule
    implements TIMMessageListener, TIMConnListener, TIMUserStatusListener {
  private static final String MODULE_NAME = "DTTimModule";
  private static final String EVT_NEW_MESSAGE = "dt.tim.EVENT_NEW_MESSAGE";
  private static final String EVT_STATE_CHANGED = "dt.tim.EVENT_STATE_CHANGED";
  private static final int ERR_SDK_NOT_INIT = -1002;
  private static final int ERR_MISSING_PARAM = -1001;
  private static final int ERR_UNKNOWN = -9999999;

  private static final int STATE_CONNECTED = 1;
  private static final int STATE_DISCONNECT = 2;
  private static final int STATE_OFFLINE = 3;
  private static final int STATE_EXPIRED = 4;
  private static final int STATE_WIFI_UNAUTH = 5;

  private static final int CHAT_TYPE_C2C = TIMConversationType.C2C.ordinal();
  private static final int CHAT_TYPE_GROUP = TIMConversationType.Group.ordinal();
  private static final int CHAT_TYPE_SYSTEM = TIMConversationType.System.ordinal();

  private static ReactApplicationContext sReactContext;
  private TIMManager mManager;
  private TIMGroupManager mGroupManager;

  DTTimModule(ReactApplicationContext reactContext) {
    super(reactContext);
    mManager = TIMManager.getInstance();
    mGroupManager = TIMGroupManager.getInstance();
    sReactContext = reactContext;
  }

  @Nullable
  @Override
  public Map<String, Object> getConstants() {
    Map<String, Object> constants = new HashMap<>();
    constants.put("EVT_NEW_MESSAGE", EVT_NEW_MESSAGE);
    constants.put("EVT_STATE_CHANGED", EVT_STATE_CHANGED);
    constants.put("ERR_SDK_NOT_INIT", ERR_SDK_NOT_INIT);
    constants.put("ERR_MISSING_PARAM", ERR_MISSING_PARAM);
    constants.put("ERR_UNKNOWN", ERR_UNKNOWN);
    constants.put("STATE_CONNECTED", STATE_CONNECTED);
    constants.put("STATE_DISCONNECT", STATE_DISCONNECT);
    constants.put("STATE_OFFLINE", STATE_OFFLINE);
    constants.put("STATE_EXPIRED", STATE_EXPIRED);
    constants.put("STATE_WIFI_UNAUTH", STATE_WIFI_UNAUTH);
    constants.put("CHAT_TYPE_C2C", CHAT_TYPE_C2C);
    constants.put("CHAT_TYPE_GROUP", CHAT_TYPE_GROUP);
    constants.put("CHAT_TYPE_SYSTEM", CHAT_TYPE_SYSTEM);
    return constants;
  }

  @Nonnull
  @Override
  public String getName() {
    return MODULE_NAME;
  }

  @ReactMethod
  public void init(ReadableMap config, Promise promise) {
    if (!mManager.isInited()) {
      if (checkForReject(config, "appId", promise))
        return;
      TIMSdkConfig sdkConfig = new TIMSdkConfig(config.getInt("appId"))
          .setLogLevel(BuildConfig.DEBUG ? TIMLogLevel.DEBUG : TIMLogLevel.OFF)
          .enableLogPrint(config.hasKey("enableLog") && config.getBoolean("enableLog"));
      mManager.init(getReactApplicationContext(), sdkConfig);
    }
    mManager.addMessageListener(this);
    resolvePromise(promise, 0, "success", null);
  }

  @ReactMethod
  public void login(ReadableMap config, Promise promise) {
    if (checkForReject(config, "identifier", promise))
      return;
    String currUid = mManager.getLoginUser();
    String identifier = config.getString("identifier");
    if (currUid != null) {
      if (currUid.equals(identifier)) {
        resolvePromise(promise, 0, "already logged in", null);
      } else {
        resolvePromise(promise, ERR_UNKNOWN, "need logout", null);
      }
      return;
    }
    if (checkForReject(config, "signature", promise))
      return;
    mManager.setUserConfig(new TIMUserConfig()
        .setConnectionListener(this)
        .setUserStatusListener(this));
    mManager.login(identifier, config.getString("signature"), new SimpleTimCallback(promise));
  }

  @ReactMethod
  public void logout(Promise promise) {
    String uid = mManager.getLoginUser();
    if (uid != null) {
      mManager.logout(new SimpleTimCallback(promise));
      return;
    }
    resolvePromise(promise, 0, "no auth", null);
  }

  @ReactMethod
  public void getUserProfile(ReadableArray users, boolean forceUpdate, final Promise promise) {
    ArrayList<String> ids = new ArrayList<>();
    for (int limit = users.size(), i = 0; i < limit; i++) {
      ids.add(users.getString(i));
    }
    TIMFriendshipManager manager = TIMFriendshipManager.getInstance();
    manager.getUsersProfile(ids, forceUpdate, new TIMValueCallBack<List<TIMUserProfile>>() {
      @Override
      public void onError(int code, String desc) {
        resolvePromise(promise, code, desc, null);
      }

      @Override
      public void onSuccess(List<TIMUserProfile> profiles) {
        WritableMap data = Arguments.createMap();
        for (TIMUserProfile profile : profiles) {
          WritableMap item = Arguments.createMap();
          item.putString("nickname", profile.getNickName());
          item.putString("avatar", profile.getFaceUrl());
          data.putMap(profile.getIdentifier(), item);
        }
        resolvePromise(promise, 0, "success", data);
      }
    });
  }

  @ReactMethod
  public void updateProfile(ReadableMap profile, Promise promise) {
    TIMFriendshipManager manager = TIMFriendshipManager.getInstance();
    manager.modifySelfProfile(profile.toHashMap(), new SimpleTimCallback(promise));
  }

  @ReactMethod
  public void sendMessage(ReadableMap message, final Promise promise) {
    if (checkForReject(message, "to", promise))
      return;
    if (checkForReject(message, "chatType", promise))
      return;
    if (checkForReject(message, "body", promise))
      return;
    String toUser = message.getString("to");
    TIMConversationType type = message.getInt("chatType") == CHAT_TYPE_GROUP
        ? TIMConversationType.Group
        : TIMConversationType.C2C;
    TIMConversation conversation = mManager.getConversation(type, toUser);
    TIMMessage msg = new TIMMessage();
    TIMTextElem textElem = new TIMTextElem();
    textElem.setText(message.getString("body"));
    msg.addElement(textElem);
    conversation.sendMessage(msg, new TIMValueCallBack<TIMMessage>() {
      @Override
      public void onError(int code, String message) {
        resolvePromise(promise, code, message, null);
      }

      @Override
      public void onSuccess(TIMMessage message) {
        resolvePromise(promise, 0, "success", buildMessageMap(message));
      }
    });
  }

  @ReactMethod
  public void joinGroup(String groupId, String reason, Promise promise) {
    mGroupManager.applyJoinGroup(groupId, reason, new SimpleTimCallback(promise));
  }

  @ReactMethod
  public void quitGroup(String groupId, Promise promise) {
    mGroupManager.quitGroup(groupId, new SimpleTimCallback(promise));
  }

  private boolean checkForReject(ReadableMap map, String key, Promise promise) {
    if (map.hasKey(key))
      return false;
    resolvePromise(promise, ERR_MISSING_PARAM, "missing param: " + key, null);
    return true;
  }

  @Override
  public void onCatalystInstanceDestroy() {
    mManager.removeMessageListener(this);
  }

  @Override
  public boolean onNewMessages(List<TIMMessage> list) {
    for (TIMMessage message : list) {
      sendEvent(EVT_NEW_MESSAGE, buildMessageMap(message));
    }
    return true;
  }

  @Override
  public void onConnected() {
    sendEvent(EVT_STATE_CHANGED, STATE_CONNECTED);
  }

  @Override
  public void onDisconnected(int i, String s) {
    sendEvent(EVT_STATE_CHANGED, STATE_DISCONNECT);
  }

  @Override
  public void onWifiNeedAuth(String s) {
    sendEvent(EVT_STATE_CHANGED, STATE_WIFI_UNAUTH);
  }

  @Override
  public void onForceOffline() {
    sendEvent(EVT_STATE_CHANGED, STATE_OFFLINE);
  }

  @Override
  public void onUserSigExpired() {
    sendEvent(EVT_STATE_CHANGED, STATE_EXPIRED);
  }

  private WritableMap buildMessageMap(TIMMessage message) {
    WritableMap map = Arguments.createMap();
    TIMConversation conversation = message.getConversation();
    map.putString("id", String.valueOf(message.getMsgUniqueId()));
    map.putString("seq", String.valueOf(message.getSeq()));
    map.putString("from", message.getSender());
    map.putString("to", message.isSelf() ? conversation.getPeer() : mManager.getLoginUser());
    map.putBoolean("isSelf", message.isSelf());
    map.putDouble("timestamp", message.timestamp() * 1000);
    map.putInt("chatType", conversation.getType().ordinal());
    TIMElem elem = message.getElement(0);
    switch (elem.getType()) {
      case Text: {
        map.putString("body", ((TIMTextElem) elem).getText());
        break;
      }
      case GroupSystem: {
        TIMGroupSystemElem e = (TIMGroupSystemElem) elem;
        WritableMap body = Arguments.createMap();
        body.putInt("type", 100); // todo
        body.putString("groupId", e.getGroupId());
        body.putString("subtype", e.getSubtype().name());
        map.putMap("body", body);
        break;
      }
      case ProfileTips: {
        TIMProfileSystemElem e = (TIMProfileSystemElem) elem;
        WritableMap body = Arguments.createMap();
        body.putInt("type", 200); // todo
        body.putString("identifier", e.getFromUser());
        String nickname;
        if (TIMProfileSystemType.TIM_PROFILE_SYSTEM_FRIEND_PROFILE_CHANGE == e.getSubType()
            && (nickname = e.getFromUser()) != null) {
          body.putString("nickname", nickname);
        }
        map.putMap("body", body);
        break;
      }
      // 自定义消息体
      case Custom: {
        TIMCustomElem e = (TIMCustomElem) elem;
        map.putString("body", new String(e.getData()));
        break;
      }
      case Invalid: {
        QLog.e("MessageInfoUtil", "invalid");
        break;
      }
    }

    return map;
  }

  private static void sendEvent(String event, Object data) {
    sReactContext.getJSModule(RCTNativeAppEventEmitter.class).emit(event, data);
  }

  private void resolvePromise(Promise promise, int status, String message, Object data) {
    WritableMap map = Arguments.createMap();
    map.putInt("status", status);
    map.putString("message", message);
    if (data instanceof WritableMap)
      map.putMap("data", (WritableMap) data);
    else if (data instanceof WritableArray)
      map.putArray("data", (WritableArray) data);
    promise.resolve(map);
  }

  private class SimpleTimCallback implements TIMCallBack {

    private Promise promise;

    private SimpleTimCallback(Promise promise) {
      this.promise = promise;
    }

    @Override
    public void onError(int code, String message) {
      resolvePromise(promise, code, message, null);
    }

    @Override
    public void onSuccess() {
      resolvePromise(promise, 0, "success", null);
    }
  }
}
