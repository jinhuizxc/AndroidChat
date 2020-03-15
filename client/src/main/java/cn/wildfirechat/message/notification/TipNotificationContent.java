package cn.wildfirechat.message.notification;

import android.os.Parcel;
import android.os.Parcelable;

import cn.wildfirechat.message.core.ContentTag;
import cn.wildfirechat.message.core.MessagePayload;
import cn.wildfirechat.message.core.PersistFlag;

import static cn.wildfirechat.message.core.MessageContentType.ContentType_Tip_Notification;

/**
 * Created by heavyrainlee on 20/12/2017.
 */

@ContentTag(type = ContentType_Tip_Notification, flag = PersistFlag.Persist)
public class TipNotificationContent extends NotificationMessageContent {
    public String tip;

    public TipNotificationContent() {
    }


    @Override
    public String formatNotification() {
        return tip;
    }

    @Override
    public MessagePayload encode() {
        MessagePayload payload = new MessagePayload();
        payload.content = tip;

        return payload;
    }

    @Override
    public void decode(MessagePayload payload) {
        tip = payload.content;
    }

    @Override
    public String digest() {
        return formatNotification();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.tip == null ? "" : this.tip);
    }

    protected TipNotificationContent(Parcel in) {
        this.tip = in.readString();
    }

    public static final Parcelable.Creator<TipNotificationContent> CREATOR = new Parcelable.Creator<TipNotificationContent>() {
        @Override
        public TipNotificationContent createFromParcel(Parcel source) {
            return new TipNotificationContent(source);
        }

        @Override
        public TipNotificationContent[] newArray(int size) {
            return new TipNotificationContent[size];
        }
    };
}
