//
//  DTTim.m
//  DTTim
//
//  Created by Yeo Yang on 2019/12/18.
//  Copyright © 2019 Beijing Dayu Intelligent Technology Co., Ltd. All rights reserved.
//

#import "DTTim.h"

static NSString *EVT_NEW_MESSAGE = @"dt.tim.EVENT_NEW_MESSAGE";
static NSString *EVT_STATE_CHANGED = @"dt.tim.EVENT_STATE_CHANGED";

@implementation RCTConvert (TimState)
RCT_ENUM_CONVERTER(TimSDKState,
                   (@{@"TimStateUnknown": @(STATE_UNKNOWN),
                      @"TimStateConnected": @(STATE_CONNECTED),
                      @"TimStateDisconnect": @(STATE_DISCONNECT),
                      @"TimStateOffline": @(STATE_OFFLINE),
                      @"TimStateExpired": @(STATE_EXPIRED),
                      @"TimstateWifiUnauth": @(STATE_WIFI_UNAUTH),
                      }),
                   STATE_UNKNOWN, integerValue);
@end
@implementation RCTConvert (TimConversationType)
RCT_ENUM_CONVERTER(TIMConversationType,
                   (@{@"CHAT_TYPE_C2C": @(TIM_C2C),
                      @"CHAT_TYPE_GROUP": @(TIM_GROUP),
                      @"CHAT_TYPE_SYSTEM": @(TIM_SYSTEM),
                      }),
                   TIM_C2C, integerValue);
@end

@implementation DTTimModule
{
    bool hasListeners;
}

RCT_EXPORT_MODULE()

- (NSArray<NSString *> *)supportedEvents {
    return @[EVT_NEW_MESSAGE, EVT_STATE_CHANGED];
}

+ (BOOL)requiresMainQueueSetup {
    return NO;
}

- (NSDictionary *)constantsToExport {
    return @{@"EVT_NEW_MESSAGE": EVT_NEW_MESSAGE,
             @"EVENT_STATE_CHANGED": EVT_STATE_CHANGED,
             @"STATE_CONNECTED": @(STATE_CONNECTED),
             @"STATE_DISCONNECT": @(STATE_DISCONNECT),
             @"STATE_OFFLINE": @(STATE_OFFLINE),
             @"STATE_EXPIRED": @(STATE_EXPIRED),
             @"STATE_WIFI_UNAUTH": @(STATE_WIFI_UNAUTH),
             @"CHAT_TYPE_C2C": @(TIM_C2C),
             @"CHAT_TYPE_GROUP": @(TIM_GROUP),
             @"CHAT_TYPE_SYSTEM": @(TIM_SYSTEM),
             };
}

- (void)startObserving {
    hasListeners = YES;
}

- (void)stopObserving {
    hasListeners = NO;
}

RCT_EXPORT_METHOD(init:(NSDictionary *)config resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    self.appId = [RCTConvert int:config[@"appId"]];
    TIMSdkConfig *sdkConfig = [[TIMSdkConfig alloc] init];
    sdkConfig.sdkAppId = self.appId;
    sdkConfig.logLevel = TIM_LOG_DEBUG;
    sdkConfig.disableLogPrint = [RCTConvert BOOL:config[@"enableLog"]];
    sdkConfig.connListener = self;

    TIMManager *manager = [TIMManager sharedInstance];
    int rv = [manager initSdk:sdkConfig];
    [manager addMessageListener:self];
    resolve(@{@"status":@(rv), @"message":rv ? @"SDK init failed" : @"success"});
}

RCT_EXPORT_METHOD(login:(NSDictionary *)config resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    TIMManager *manager = [TIMManager sharedInstance];
    TIMUserConfig *userConfig = [[TIMUserConfig alloc] init];
    userConfig.userStatusListener = self;
    TIMFriendProfileOption *option = [[TIMFriendProfileOption alloc] init];
    option.expiredSeconds = 2 * 60 * 60; // 2 hours
    userConfig.friendProfileOpt = option;
    [manager setUserConfig:userConfig];

    TIMLoginParam *param = [[TIMLoginParam alloc] init];
    param.identifier = config[@"identifier"];
    param.userSig = config[@"signature"];
    param.appidAt3rd = [NSString stringWithFormat:@"%d", self.appId];
    [manager login:param
              succ:^() {
                  resolve(@{@"status":@(0), @"message":@"success"});
              }
              fail:^(int code, NSString *err) {
                  resolve(@{@"status":@(code), @"message":err});
              }];
}

RCT_EXPORT_METHOD(logout:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    TIMLoginSucc succ = ^() {
        resolve(@{@"status":@(0), @"message":@"success"});
    };
    TIMFail fail = ^(int code, NSString *err) {
        resolve(@{@"status":@(code), @"message":err});
    };
    [[TIMManager sharedInstance] logout:succ fail:fail];
}

RCT_EXPORT_METHOD(getUserProfile:(NSArray *)users
                  forceUpdate:(BOOL)forceUpdate
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    [[TIMFriendshipManager sharedInstance] getUsersProfile:users
                                               forceUpdate:forceUpdate
                                                      succ:^(NSArray *profiles) {
                                                          NSMutableDictionary *data = [[NSMutableDictionary alloc] initWithCapacity:[profiles count]];
                                                          for (TIMUserProfile *profile in profiles) {
                                                              [data setObject:@{@"nickname":[profile nickname], @"avatar":[profile faceURL]}
                                                                       forKey:[profile identifier]];
                                                          }
                                                          resolve(@{@"status":@(0), @"data":data});
                                                      }
                                                      fail:^(int code, NSString *err) {
                                                          resolve(@{@"status":@(code), @"message":err});
                                                      }];
}

RCT_EXPORT_METHOD(updateProfile:(NSDictionary *)profile
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    NSDictionary *profileDic = @{TIMProfileTypeKey_Nick:[profile objectForKey:@"nickname"],
                                 TIMProfileTypeKey_FaceUrl:[profile objectForKey:@"avatar"],
                                 };
    [[TIMFriendshipManager sharedInstance] modifySelfProfile:profileDic
                                                        succ:^() {
                                                            resolve(@{@"status":@(0), @"message":@"success"});
                                                        }
                                                        fail:^(int code, NSString *err) {
                                                            resolve(@{@"status":@(code), @"message":err});
                                                        }];
}

RCT_EXPORT_METHOD(sendMessage:(NSDictionary *)message
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    TIMConversationType chatType = [RCTConvert TIMConversationType:message[@"chatType"]];
    TIMConversation *conversation = [[TIMManager sharedInstance] getConversation:chatType receiver:message[@"to"]];

    TIMTextElem *elem = [[TIMTextElem alloc] init];
    [elem setText:message[@"body"]];
    TIMMessage *timMsg = [[TIMMessage alloc] init];
    [timMsg addElem:elem];
    [conversation sendMessage:timMsg
                         succ:^() {
                             resolve(@{@"status":@(0), @"message":@"success", @"data":[self convertToMap:timMsg]});
                         }
                         fail:^(int code, NSString *err) {
                             resolve(@{@"status":@(code), @"message":err});
                         }];
}

RCT_EXPORT_METHOD(joinGroup:(NSString *)groupId
                  reason:(NSString *)reason
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    [[TIMGroupManager sharedInstance] joinGroup:groupId
                                            msg:reason
                                           succ:^() {
                                               resolve(@{@"status":@(0), @"message":@"success"});
                                           }
                                           fail:^(int code, NSString *err) {
                                               resolve(@{@"status":@(code), @"message":err});
                                           }];
}

RCT_EXPORT_METHOD(quitGroup:(NSString *)groupId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    [[TIMGroupManager sharedInstance] quitGroup:groupId
                                           succ:^() {
                                               resolve(@{@"status":@(0), @"message":@"success"});
                                           }
                                           fail:^(int code, NSString *err) {
                                               resolve(@{@"status":@(code), @"message":err});
                                           }];
}

- (NSDictionary *)convertToMap:(TIMMessage *)message {
    TIMConversation *conversation = [message getConversation];
    id body = @"";
    if ([message elemCount] > 0) {
        TIMElem *elem = [message getElem:0];
        if ([elem isKindOfClass:[TIMTextElem class]]) {
            body = ((TIMTextElem *)elem).text;
        } else if ([elem isKindOfClass:[TIMGroupSystemElem class]]) {
            TIMGroupSystemElem *e = (TIMGroupSystemElem *)elem;
            body = @{@"type":@(100), @"groupId":[e group], @"subtype":@([e type])};
        } else if ([elem isKindOfClass:[TIMProfileSystemElem class]]) {
            TIMProfileSystemElem *e = (TIMProfileSystemElem *)elem;
            body = [[NSMutableDictionary alloc] init];
            [body setObject:@(200) forKey:@"type"];
            [body setObject:[e fromUser] forKey:@"identifier"];
            if ([e nickName] != nil) {
                [body setObject:[e nickName] forKey:@"nickname"];
            }
        }else if ([elem isKindOfClass:[TIMCustomElem class]]) {
            TIMCustomElem *e = (TIMCustomElem *)elem;
            body = [[NSString alloc] initWithData:[e data] encoding:NSUTF8StringEncoding];
        }
    }
    return @{@"id": [NSString stringWithFormat:@"%llu", [message uniqueId]],
             @"seq": [NSString stringWithFormat:@"%llu", message.locator.seq],
             @"from": [message sender],
             @"to": [message isSelf] ? [conversation getReceiver] : [[TIMManager sharedInstance] getLoginUser],
             @"isSelf": @([message isSelf]),
             @"chatType": @([conversation getType]),
             @"timestamp": @([[message timestamp] timeIntervalSince1970] * 1000),
             @"body": body,
             };
}

- (void)onNewMessage:(NSArray *) msgs {
    if (!hasListeners) return;
    for (TIMMessage *message in msgs) {
        [self sendEventWithName:EVT_NEW_MESSAGE body:[self convertToMap:message]];
    }
}
- (void)onConnSucc {
    if (!hasListeners) return;
    [self sendEventWithName:EVT_STATE_CHANGED body:@(STATE_CONNECTED)];
}
- (void)onConnFailed:(int)code err:(NSString *)err {
    if (!hasListeners) return;
    [self sendEventWithName:EVT_STATE_CHANGED body:@(STATE_DISCONNECT)];
}
- (void)onDisconnect:(int)code err:(NSString *)err {
    if (!hasListeners) return;
    [self sendEventWithName:EVT_STATE_CHANGED body:@(STATE_DISCONNECT)];
}
- (void)onForceOffline {
    if (!hasListeners) return;
    [self sendEventWithName:EVT_STATE_CHANGED body:@(STATE_OFFLINE)];
}
- (void)onUserSigExpired {
    if (!hasListeners) return;
    [self sendEventWithName:EVT_STATE_CHANGED body:@(STATE_EXPIRED)];
}

@end
