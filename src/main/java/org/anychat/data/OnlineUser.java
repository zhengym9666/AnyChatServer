package org.anychat.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import javax.websocket.Session;

import org.anychat.action.ChatAction;
import org.anychat.action.ChatGroupUserAction;
import org.anychat.config.ChatConfig;
import org.anychat.model.base.Chat;
import org.anychat.model.base.ChatGroupUser;
import org.anychat.msg.MsgOpCodeChat;
import org.anychat.protobuf.msg.UserOfflineMsg.UserOffline;
import org.anychat.protobuf.ws.LoginChatProto.GroupMessageS;
import org.anychat.protobuf.ws.LoginChatProto.UserKickS;
import org.anychat.protobuf.ws.LoginChatProto.UserMessageS;
import org.anychat.ws.WsOpCodeChat;
import org.grain.thread.AsyncThreadManager;
import org.grain.thread.ICycle;
import org.grain.threadmsg.ThreadMsgManager;
import org.grain.websokcetlib.WSManager;
import org.grain.websokcetlib.WsPacket;

public class OnlineUser implements ICycle {
	private UserData userData;
	private Session session;
	private String token;
	private boolean isKick = false;
	private boolean isDisConnect = false;
	// 自身在哪个线程，哪个优先级
	private int[] threadPriority;
	private LinkedBlockingQueue<Chat> userChatQueue = new LinkedBlockingQueue<Chat>();
	private ArrayList<Chat> handleUserChat = new ArrayList<Chat>();
	private LinkedBlockingQueue<Chat> groupChatQueue = new LinkedBlockingQueue<Chat>();
	private ArrayList<Chat> handleGroupChat = new ArrayList<Chat>();

	public OnlineUser(UserData userData, Session session, String token) {
		this.userData = userData;
		this.session = session;
		this.token = token;
	}

	public UserData getUserData() {
		return userData;
	}

	public String getToken() {
		return token;
	}

	public String getUserId() {
		return userData.getUserId();
	}

	public String getSessionId() {
		return session.getId();
	}

	public boolean isKick() {
		return isKick;
	}

	public void setKick(boolean isKick) {
		if (!this.isKick) {
			UserKickS.Builder builder = UserKickS.newBuilder();
			builder.setWsOpCode(WsOpCodeChat.USER_KICK_S);
			builder.setMsg("xxx");
			WsPacket wsPacket = new WsPacket(WsOpCodeChat.USER_KICK_S, builder.build());
			send(wsPacket);
		}
		this.isKick = isKick;
	}

	public boolean isDisConnect() {
		return isDisConnect;
	}

	public void setDisConnect(boolean isDisConnect) {
		this.isDisConnect = isDisConnect;
	}

	public int[] getThreadPriority() {
		return threadPriority;
	}

	public void setThreadPriority(int[] threadPriority) {
		this.threadPriority = threadPriority;
	}

	public void close() {
		if (!isDisConnect) {
			try {
				this.session.close();
			} catch (IOException e) {
				WSManager.log.error("关闭session异常", e);
			}
		}
	}

	public void clear() {
		close();
		userData = null;
		session = null;
		userChatQueue.clear();
		userChatQueue = null;
		handleUserChat.clear();
		handleUserChat = null;
		groupChatQueue.clear();
		groupChatQueue = null;
		handleGroupChat.clear();
		handleGroupChat = null;
	}

	public boolean send(WsPacket wsPacket) {
		try {
			if (session.isOpen()) {
				session.getBasicRemote().sendObject(wsPacket);
			}
			return true;
		} catch (Exception e) {
			WSManager.log.error("发送给客户端异常wsOpcode:" + wsPacket.getWsOpCode(), e);
			return false;
		}
	}

	public boolean addUserChatQueue(Chat chat) {
		try {
			userChatQueue.put(chat);
			return true;
		} catch (InterruptedException e) {
			WSManager.log.error("addUserChatQueue error", e);
			return false;
		}
	}

	public boolean addGroupChatQueue(Chat chat) {
		try {
			groupChatQueue.put(chat);
			return true;
		} catch (InterruptedException e) {
			WSManager.log.error("addGroupChatQueue error", e);
			return false;
		}
	}

	public ArrayList<Chat> getHandleUserChat() {
		handleUserChat.clear();
		userChatQueue.drainTo(handleUserChat);
		return handleUserChat;
	}

	public ArrayList<Chat> getHandleGroupChat() {
		handleGroupChat.clear();
		groupChatQueue.drainTo(handleGroupChat);
		return handleGroupChat;
	}

	@Override
	public void cycle() throws Exception {
		// 扫描自身消息队列发送消息
		// 如果被踢或者自行断开，走下线流程
		if (isKick || isDisConnect) {
			AsyncThreadManager.removeCycle(this, threadPriority[0], threadPriority[1]);
			return;
		}
		ArrayList<Chat> userChatArray = getHandleUserChat();
		ArrayList<Chat> groupChatArray = getHandleGroupChat();
		// 发送此次轮训的用户聊天
		if (userChatArray.size() != 0) {
			Map<String, List<Chat>> userChatMap = getUserChatMap(userChatArray);
			sendUserChat(userChatMap);
		}
		// 发送此次轮训的组聊天
		if (groupChatArray.size() != 0) {
			Map<String, List<Chat>> groupChatMap = getGroupChatMap(groupChatArray);
			sendGroupChat(groupChatMap);
		}
	}

	@Override
	public void onAdd() throws Exception {
		// 加入场景时xxx
		// 发送用户数据
		List<Chat> chatList = ChatAction.getChatList(ChatConfig.TO_TYPE_USER, getUserId(), ChatConfig.CHAT_TYPE_SEND, null);
		if (chatList != null && chatList.size() != 0) {
			Map<String, List<Chat>> chatMap = getUserChatMap(chatList);
			sendUserChat(chatMap);
		}
		// 发送组数据
		List<ChatGroupUser> chatGroupUserList = ChatGroupUserAction.getChatGroupUserList(getUserId(), null);
		if (chatGroupUserList != null && chatGroupUserList.size() != 0) {
			for (int i = 0; i < chatGroupUserList.size(); i++) {
				ChatGroupUser chatGroupUser = chatGroupUserList.get(i);
				chatList = ChatAction.getChatList(ChatConfig.TO_TYPE_GROUP, chatGroupUser.getChatGroupId(), 0, chatGroupUser.getChatGroupUserUpdateTime());
				if (chatList != null && chatList.size() > 0) {
					sendGroupChat(chatList, chatGroupUser.getChatGroupId());
				}
			}
		}
	}

	public void sendGroupChat(Map<String, List<Chat>> chatMap) {
		Object[] keySetArray = chatMap.keySet().toArray();
		for (int i = 0; i < keySetArray.length; i++) {
			String chatGroupId = String.valueOf(keySetArray[i]);
			List<Chat> chatList = chatMap.get(chatGroupId);
			sendGroupChat(chatList, chatGroupId);
		}
	}

	public void sendGroupChat(List<Chat> chatList, String chatGroupId) {
		GroupMessageS.Builder builder = GroupMessageS.newBuilder();
		builder.setChatGroupId(chatGroupId);
		builder.setWsOpCode(WsOpCodeChat.GROUP_MESSAGE_S);
		for (int j = 0; j < chatList.size(); j++) {
			Chat chat = chatList.get(j);
			builder.addMessage(ChatAction.getChatMessageDataBuilder(chat));
		}
		// 用户已经不在这个组里也没关系，接收到不处理就好了
		WsPacket wsPacket = new WsPacket(WsOpCodeChat.GROUP_MESSAGE_S, builder.build());
		send(wsPacket);
	}

	public void sendUserChat(Map<String, List<Chat>> chatMap) {
		Object[] keySetArray = chatMap.keySet().toArray();
		for (int i = 0; i < keySetArray.length; i++) {
			String userId = String.valueOf(keySetArray[i]);
			List<Chat> chatList = chatMap.get(userId);
			UserMessageS.Builder builder = UserMessageS.newBuilder();
			builder.setUserId(userId);
			builder.setWsOpCode(WsOpCodeChat.USER_MESSAGE_S);
			for (int j = 0; j < chatList.size(); j++) {
				Chat chat = chatList.get(j);
				builder.addMessage(ChatAction.getChatMessageDataBuilder(chat));
			}
			// 非现在用户列表的消息发出去也没关系，用户不接受就好了
			WsPacket wsPacket = new WsPacket(WsOpCodeChat.USER_MESSAGE_S, builder.build());
			send(wsPacket);
		}
	}

	public static Map<String, List<Chat>> getUserChatMap(List<Chat> chatList) {
		Map<String, List<Chat>> chatMap = new HashMap<String, List<Chat>>();
		for (int i = 0; i < chatList.size(); i++) {
			Chat chat = chatList.get(i);
			List<Chat> userChatList = null;
			if (!chatMap.containsKey(chat.getFromUserId())) {
				userChatList = new ArrayList<Chat>();
				chatMap.put(chat.getFromUserId(), userChatList);
			}
			userChatList = chatMap.get(chat.getFromUserId());
			userChatList.add(chat);
		}
		return chatMap;
	}

	public static Map<String, List<Chat>> getGroupChatMap(List<Chat> chatList) {
		Map<String, List<Chat>> chatMap = new HashMap<String, List<Chat>>();
		for (int i = 0; i < chatList.size(); i++) {
			Chat chat = chatList.get(i);
			List<Chat> groupChatList = null;
			if (!chatMap.containsKey(chat.getToTypeId())) {
				groupChatList = new ArrayList<Chat>();
				chatMap.put(chat.getToTypeId(), groupChatList);
			}
			groupChatList = chatMap.get(chat.getToTypeId());
			groupChatList.add(chat);
		}
		return chatMap;
	}

	@Override
	public void onRemove() throws Exception {
		// 离开场景时xxx
		// 发布离线消息
		UserOffline.Builder builder = UserOffline.newBuilder();
		builder.setUserId(getUserId());
		ThreadMsgManager.dispatchThreadMsg(MsgOpCodeChat.USER_OFFLINE, builder.build(), this);
	}
}
