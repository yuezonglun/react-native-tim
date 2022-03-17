import { NativeModules, NativeEventEmitter } from 'react-native';

const { DTTimModule } = NativeModules;
const EventEmitter = new NativeEventEmitter(DTTimModule);

type TimLoginConfig = {
    identifier: string,
    signature: string,
}

type TimInitConfig = {
    appId: number,
    enableLog?: boolean,
    user?: TimLoginConfig,
}

type TimProfile = {
    avatar?: string,
    nickname?: string,
}

type TimMessage = {
    to: string,
    chatType: number,
    body: string | Object,
}

class Subscription {
    constructor(event, callback) {
        this.event = event;
        this.callback = callback;
    }

    emit(event, data) {
        if (this.event === event) this.callback(data);
    }
}

class TimModule {
    C2C = DTTimModule['CHAT_TYPE_C2C'];
    GROUP = DTTimModule['CHAT_TYPE_GROUP'];
    SYSTEM = DTTimModule['CHAT_TYPE_SYSTEM'];

    EVT_MESSAGE = 'TIM.MESSAGE';

    _uid = null;
    _listeners = [];

    constructor() {
        EventEmitter.addListener(DTTimModule['EVT_NEW_MESSAGE'], this.handleNewMessage);
    }

    get currentUser() {
        return this._uid;
    }

    get isAuth() {
        return this._uid !== null;
    }

    init = (config: TimInitConfig) => {
        const { user, ...conf } = config;
        return DTTimModule.init(conf).then(resp => resp.status === 0 && user ? this.login(user) : resp);
    };

    login = (config: TimLoginConfig) => DTTimModule.login(config).then(resp => {
        this._uid = resp.status === 0 ? config.identifier : null;
        console.log(resp,'imlogin====')
        return resp;
    });
    logout = () => DTTimModule.logout();

    updateProfile = (profile: TimProfile) => DTTimModule.updateProfile(profile);
    getUserProfile = (ids: string[], forceUpdate: boolean = false) => DTTimModule.getUserProfile(ids, forceUpdate);

    joinGroup = (roomId: string) => DTTimModule.joinGroup(roomId);
    quitGroup = (roomId: string) => DTTimModule.quitGroup(roomId);

    sendMessage = (message: TimMessage) => {
        return DTTimModule.sendMessage({
            ...message,
            body: JSON.stringify(message.body),
        }).then(resp => {
            if (resp.status !== 0) return resp;
            const { data: { body, ...data }, ...other } = resp;
            return {
                ...other,
                data: {
                    ...data,
                    body: typeof body !== 'string' || body === '' ? body : JSON.parse(body),
                },
            };
        });
    };

    handleNewMessage = ({ body, ...msg }) => {
        const message = {
            ...msg,
            body: typeof body !== 'string' || body === '' ? body : JSON.parse(body),
        };
        this._listeners.forEach(subscription => subscription.emit(this.EVT_MESSAGE, message));
    };

    subscribe = (event, callback) => {
        const subscription = new Subscription(event, callback);
        this._listeners.push(subscription);
        return () => {
            this._listeners = this._listeners.filter(item => item !== subscription);
        };
    };
}

export default new TimModule();
