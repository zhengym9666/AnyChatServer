package org.anychat.action;/**
 * @ClassName:
 * @Description: TODO
 * @author linyb3
 * @date
 */

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.anychat.config.CommonConfigChat;
import org.anychat.data.UserData;
import org.anychat.data.UserGroupData;
import org.grain.httpclient.HttpUtil;
import org.grain.websokcetlib.WSManager;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.anychat.protobuf.ws.LoginChatProto.ChatUserData;
/**
 * @ClassName:
 * @Description: TODO
 * @author linyb3
 * @date
 *
 */
public interface IdentityActionAbs {
    /**
     * 获取用户信息
     *
     * @param token
     *            身份
     * @return
     */
    public UserData getUser(String token);

    /**
     * 获取好友列表相当于组里的所有人
     *
     * @param userGroupTopId
     *            组id
     * @param token
     *            身份
     * @return
     */
    public List<UserData> getFriendList(String userGroupTopId, String token);

    /**
     * 获取组的信息
     *
     * @param userGroupId
     *            组id
     * @param token
     *            身份
     * @return
     */
    public UserGroupData getUserGroup(String userGroupId, String token);

    /**
     * 构建用户信息数据
     *
     * @param userData
     *            用户数据
     * @param isOnline
     *            是否在线
     * @return
     */
    public ChatUserData.Builder getChatUserDataBuilder(UserData userData, boolean isOnline);
}
