package com.fredrikbogg.android_chat_app.ui.chats

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.*
import com.fredrikbogg.android_chat_app.data.Event
import com.fredrikbogg.android_chat_app.data.Result
import com.fredrikbogg.android_chat_app.data.db.entity.Chat
import com.fredrikbogg.android_chat_app.data.model.ChatWithUserInfo
import com.fredrikbogg.android_chat_app.data.db.entity.UserFriend
import com.fredrikbogg.android_chat_app.data.db.entity.UserInfo
import com.fredrikbogg.android_chat_app.data.db.remote.FirebaseReferenceValueObserver
import com.fredrikbogg.android_chat_app.data.db.repository.DatabaseRepository
import com.fredrikbogg.android_chat_app.ui.DefaultViewModel
import com.fredrikbogg.android_chat_app.util.addNewItem
import com.fredrikbogg.android_chat_app.util.convertTwoUserIDs
import com.fredrikbogg.android_chat_app.util.updateItemAt


class ChatsViewModelFactory(private val myUserID: String) :
    ViewModelProvider.Factory {
    @RequiresApi(Build.VERSION_CODES.N)
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ChatsViewModel(myUserID) as T
    }
}

@RequiresApi(Build.VERSION_CODES.N)
class ChatsViewModel(val myUserID: String) : DefaultViewModel() {

    private val repository: DatabaseRepository = DatabaseRepository()
    private val firebaseReferenceObserverList = ArrayList<FirebaseReferenceValueObserver>()
    private val _updatedChatWithUserInfo = MutableLiveData<ChatWithUserInfo>()
    private val _selectedChat = MutableLiveData<Event<ChatWithUserInfo>>()

    var selectedChat: LiveData<Event<ChatWithUserInfo>> = _selectedChat
    val chatsList = MediatorLiveData<MutableList<ChatWithUserInfo>>()

    init {
        chatsList.addSource(_updatedChatWithUserInfo) { newChat ->
            val chat = chatsList.value?.find { it.mChat.info.id == newChat.mChat.info.id }
            if (chat == null) {
                chatsList.addNewItem(newChat)
            } else {
                chatsList.updateItemAt(newChat, chatsList.value!!.indexOf(chat))
            }
        }
        setupChats()
    }

    override fun onCleared() {
        super.onCleared()
        firebaseReferenceObserverList.forEach { it.clear() }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupChats() {
        loadFriends()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun loadFriends() {
        repository.loadFriends(myUserID) { result: Result<List<UserFriend>> ->
            onResult(null, result)
            if (result is Result.Success) result.data?.forEach { loadUserInfo(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun loadUserInfo(userFriend: UserFriend) {
        repository.loadUserInfo(userFriend.userID) { result: Result<UserInfo> ->
            onResult(null, result)
            if (result is Result.Success) result.data?.let { loadAndObserveChat(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun loadAndObserveChat(userInfo: UserInfo) {
        val observer = FirebaseReferenceValueObserver()
        firebaseReferenceObserverList.add(observer)
        repository.loadAndObserveChat(convertTwoUserIDs(myUserID, userInfo.id), observer) { result: Result<Chat> ->
            if (result is Result.Success) {
                _updatedChatWithUserInfo.value = result.data?.let { ChatWithUserInfo(it, userInfo) }
            } else if (result is Result.Error) {
                chatsList.value?.let {
                    val newList = mutableListOf<ChatWithUserInfo>().apply { addAll(it) }
                    newList.removeIf { it2 -> result.msg.toString().contains(it2.mUserInfo.id) }
                    chatsList.value = newList
                }
            }
        }
    }

    fun selectChatWithUserInfoPressed(chat: ChatWithUserInfo) {
        _selectedChat.value = Event(chat)
    }
}