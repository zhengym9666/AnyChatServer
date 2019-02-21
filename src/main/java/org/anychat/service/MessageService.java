package org.anychat.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.websocket.Session;

import org.anychat.action.ChatAction;
import org.anychat.action.ChatGroupUserAction;
import org.anychat.config.ChatConfig;
import org.anychat.data.OnlineUser;
import org.anychat.data.OnlineUserManager;
import org.anychat.model.base.Chat;
import org.anychat.model.base.ChatGroupUser;
import org.anychat.protobuf.ws.LoginChatProto.GetChatListC;
import org.anychat.protobuf.ws.LoginChatProto.GetChatListS;
import org.anychat.protobuf.ws.LoginChatProto.GroupMessageReceiveC;
import org.anychat.protobuf.ws.LoginChatProto.SendMessageC;
import org.anychat.protobuf.ws.LoginChatProto.ToUserMessageS;
import org.anychat.protobuf.ws.LoginChatProto.UserMessageReceiveC;
import org.anychat.tool.PageFormat;
import org.anychat.tool.PageObj;
import org.anychat.tool.StringUtil;
import org.anychat.ws.WsOpCodeChat;
import org.grain.websokcetlib.IWSListener;
import org.grain.websokcetlib.WSManager;
import org.grain.websokcetlib.WsPacket;

public class MessageService implements IWSListener {

	@Override
	public Map<String, String> getWSs() throws Exception {
		HashMap<String, String> map = new HashMap<>();
		map.put(WsOpCodeChat.SEND_MESSAGE_C, "sendMessageHandle");
		map.put(WsOpCodeChat.USER_MESSAGE_RECEIVE_C, "userMessageReceiveHandle");
		map.put(WsOpCodeChat.GROUP_MESSAGE_RECEIVE_C, "groupMessageReceiveHandle");
		map.put(WsOpCodeChat.GET_CHAT_LIST_C, "getChatListHandle");
		return map;
	}

	/**
	 * 随机线程
	 * 
	 * @param wsPacket
	 */
	public void sendMessageHandle(WsPacket wsPacket) {
		Session session = (Session) wsPacket.session;
		OnlineUser onlineUser = OnlineUserManager.getOnlineUserBySessionId(session.getId());
		if (onlineUser == null) {
			WSManager.log.warn("发消息的非在线用户，直接返回");
			return;
		}
		SendMessageC builder1 = (SendMessageC) wsPacket.getData();
		Chat chat = ChatAction.createChat(builder1.getChatContent(), builder1.getToType(), builder1.getToTypeId(), onlineUser.getUserId());
		if (chat == null) {
			// 通知用户发送失败
			return;
		}
		if (chat.getToType() == ChatConfig.TO_TYPE_USER) {
			// 如果发送给用户,如果用户在线，放入在线用户消息队列里
			OnlineUser sendOnlineUser = OnlineUserManager.getOnlineUserByUserId(chat.getToTypeId());
			if (sendOnlineUser != null) {
				sendOnlineUser.addUserChatQueue(chat);
			}
			// 广播给自己，服务器发送到客户端，由客户端自己处理，不是由服务器处理
			ToUserMessageS.Builder builder = ToUserMessageS.newBuilder();
			builder.setToUserId(chat.getToTypeId());
			builder.setWsOpCode(WsOpCodeChat.TO_USER_MESSAGE_S);
			builder.setMessage(ChatAction.getChatMessageDataBuilder(chat));
			WsPacket sendWsPacket = new WsPacket(WsOpCodeChat.TO_USER_MESSAGE_S, builder.build());
			onlineUser.send(sendWsPacket);
		} else if (chat.getToType() == ChatConfig.TO_TYPE_GROUP) {
			List<ChatGroupUser> chatGroupUserList = ChatGroupUserAction.getChatGroupUserList(null, chat.getToTypeId());
			if (chatGroupUserList != null && chatGroupUserList.size() != 0) {
				for (int i = 0; i < chatGroupUserList.size(); i++) {
					ChatGroupUser chatGroupUser = chatGroupUserList.get(i);
					OnlineUser friendOnlineUser = OnlineUserManager.getOnlineUserByUserId(chatGroupUser.getUserId());
					if (friendOnlineUser != null) {
						friendOnlineUser.addGroupChatQueue(chat);
					}
				}
			}
		}
	}

	/**
	 * 随机线程
	 * 
	 * @param wsPacket
	 */
	public void userMessageReceiveHandle(WsPacket wsPacket) {
		Session session = (Session) wsPacket.session;
		OnlineUser onlineUser = OnlineUserManager.getOnlineUserBySessionId(session.getId());
		if (onlineUser == null) {
			WSManager.log.warn("发消息的非在线用户，直接返回");
			return;
		}
		UserMessageReceiveC builder1 = (UserMessageReceiveC) wsPacket.getData();
		boolean result = ChatAction.updateChat(builder1.getMessageIdList());
	}

	/**
	 * 随机线程
	 * 
	 * @param wsPacket
	 */
	public void groupMessageReceiveHandle(WsPacket wsPacket) {
		Session session = (Session) wsPacket.session;
		OnlineUser onlineUser = OnlineUserManager.getOnlineUserBySessionId(session.getId());
		if (onlineUser == null) {
			WSManager.log.warn("发消息的非在线用户，直接返回");
			return;
		}
		GroupMessageReceiveC builder1 = (GroupMessageReceiveC) wsPacket.getData();
		Chat chat = ChatAction.getChatById(builder1.getEndChatId());
		if (chat == null) {
			wsPacket.putMonitor("获取聊天记录为空，请查看问题");
			return;
		}
		ChatGroupUser chatGroupUser = ChatGroupUserAction.updateChatGroupUser(onlineUser.getUserId(), chat.getToTypeId(), chat.getChatCreateTime());
		if (chatGroupUser == null) {
			wsPacket.putMonitor("修改组内用户更新时间失败，请查看问题");
			return;
		}
		// 修改成功
	}

	/**
	 * 随机线程
	 * 
	 * @param wsPacket
	 */
	public void getChatListHandle(WsPacket wsPacket) {
		Session session = (Session) wsPacket.session;
		OnlineUser onlineUser = OnlineUserManager.getOnlineUserBySessionId(session.getId());
		if (onlineUser == null) {
			WSManager.log.warn("发消息的非在线用户，直接返回");
			return;
		}
		GetChatListC builder1 = (GetChatListC) wsPacket.getData();
		int currentPage = builder1.getCurrentPage();
		int pageSize = builder1.getPageSize();
		PageObj pageObj = null;
		List<Chat> chatList = null;
		if (builder1.getToType() == ChatConfig.TO_TYPE_USER) {
			if (!StringUtil.stringIsNull(builder1.getChatCreateTime())) {
				long num = ChatAction.getUserChatNum(builder1.getToTypeId(), onlineUser.getUserId(), builder1.getChatCreateTime());
				currentPage = (int) Math.ceil((num + 1.0) / pageSize);
			}
			int allNum = (int) ChatAction.getUserChatNum(builder1.getToTypeId(), onlineUser.getUserId(), null);
			pageObj = PageFormat.getStartAndEnd(currentPage, pageSize, allNum);
			chatList = ChatAction.getUserChatList(builder1.getToTypeId(), onlineUser.getUserId(), pageObj.start, pageObj.pageSize);
		} else if (builder1.getToType() == ChatConfig.TO_TYPE_GROUP) {

			if (!StringUtil.stringIsNull(builder1.getChatCreateTime())) {
				long num = ChatAction.getGroupChatNum(builder1.getToTypeId(), builder1.getChatCreateTime());
				currentPage = (int) Math.ceil((num + 1.0) / pageSize);
			}
			int allNum = (int) ChatAction.getGroupChatNum(builder1.getToTypeId(), null);
			pageObj = PageFormat.getStartAndEnd(currentPage, pageSize, allNum);
			chatList = ChatAction.getGroupChatList(builder1.getToTypeId(), pageObj.start, pageObj.pageSize);
		} else {
			return;
		}
		if (chatList != null) {
			GetChatListS.Builder builder = GetChatListS.newBuilder();
			builder.setWsOpCode(WsOpCodeChat.GET_CHAT_LIST_S);
			builder.setCurrentPage(pageObj.currentPage);
			builder.setPageSize(pageObj.pageSize);
			builder.setTotalPage(pageObj.totalPage);
			builder.setAllNum(pageObj.allNum);
			builder.setToType(builder1.getToType());
			builder.setToTypeId(builder1.getToTypeId());
			for (int i = 0; i < chatList.size(); i++) {
				builder.addMessage(ChatAction.getChatMessageDataBuilder(chatList.get(i)));
			}
			WsPacket returnWsPacket = new WsPacket(WsOpCodeChat.GET_CHAT_LIST_S, builder.build());
			onlineUser.send(returnWsPacket);
		}
	}

}
