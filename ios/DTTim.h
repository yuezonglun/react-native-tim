//
//  DTTim.h
//  DTTim
//
//  Created by Yeo Yang on 2019/12/18.
//  Copyright © 2019 Beijing Dayu Intelligent Technology Co., Ltd. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTConvert.h>
#import <React/RCTEventEmitter.h>
#import <ImSDK/ImSDK.h>

typedef NS_ENUM(NSInteger, TimSDKState) {
  STATE_UNKNOWN = 0,
  STATE_CONNECTED = 1,
  STATE_DISCONNECT = 2,
  STATE_OFFLINE = 3,
  STATE_EXPIRED = 4,
  STATE_WIFI_UNAUTH = 5,
};

typedef NS_ENUM(NSInteger, TimMessageType) {
  BODY_TYPE_UNKNOWN = 0,
  BODY_TYPE_TEXT = 1,
  BODY_TYPE_IMAGE = 2,
  BODY_TYPE_GROUP_SYSTEM = 3,
  BODY_TYPE_PROFILE_CHANGE = 4,
};

@interface DTTimModule : RCTEventEmitter <RCTBridgeModule, TIMConnListener, TIMMessageListener, TIMUserStatusListener, TIMMessageUpdateListener>

@property(atomic) int appId;

@end
