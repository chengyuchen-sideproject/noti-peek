# NotiPeek

[English](README.md) | 繁體中文

一款 Android App,讓你**看 LINE(以及其他任何 App)的新訊息卻不會變成已讀**。它把
通知收下來存在本機,所以你可以偷看 LINE 訊息而不用打開聊天室 —— LINE 不會送出已讀回條。

> **狀態:初版骨架(v0.1.0)。** 核心攔截 + 本地儲存 + 最小 UI 已完成。尚未在實機
> 測試,請用 Android Studio 建置後執行。

## 運作原理 —— 以及為什麼不會變已讀

LINE 只有在**你打開聊天室**時才送已讀回條。NotiPeek 從不打開聊天室,而是註冊一支
**`NotificationListenerService`**:一旦你授予「通知存取」權限,Android 會把每一則
通知的副本送給這個 App。讀取通知是系統層的動作,來源 App 完全看不到 —— 所以用這種
方式讀到的 LINE 訊息,在 LINE 裡仍維持**未讀**。

這是唯一可行的做法:LINE 沒有任何可讀取訊息的公開 API,通知串流是唯一來源。

## 誠實說明的限制(機制天生如此,不是 bug)

- **只能收到「授權期間」到達的新訊息**,抓不到歷史訊息 —— 沒被當成通知送來的就是沒有。
- **LINE 通知必須開著**;你把 LINE 靜音就什麼都收不到。
- **貼圖 / 圖片 / 語音**通常只會顯示佔位文字(「[貼圖]」「[照片]」)—— 實際內容不在通知裡。
- **很長的訊息可能被截斷**成通知預覽。
- 通訊 App 每來一則新訊息就會重貼整個對話;NotiPeek 會**去重**,每則只存一次。

## 功能

- 攔截**任何** App 的通知(通用偷看器,LINE 是主要使用情境)。
- 從 `MessagingStyle` 通知(LINE、WhatsApp、Messenger…)拆出**每一則訊息**,含寄件者
  與時間;其他 App 則退回 inbox / big-text / 純文字樣式解析。
- 用 Room 資料庫保留**本地歷史** —— 通知被清掉也不會遺失。
- 依 App 分組,點進去看訊息串;可清除單一 App 或全部。
- **隱私優先設計**:App **未宣告 `INTERNET` 權限** —— 收到的訊息絕不離開這支手機。

## 需求與相容性

- **Android 8.0(API 26)以上** —— `minSdk 26`、`targetSdk 34`、`compileSdk 34`。
- 以 **AGP 8.5.2 / Gradle 8.9 / Kotlin 1.9.24 / Jetpack Compose(BOM 2024.09)/
  Room 2.6.1** 建置,JDK 17。
- 無任何第三方服務,只用 AndroidX / Jetpack 函式庫。

## 建置與執行

1. 用 **Android Studio**(Koala / 2024.1+)開啟專案,首次 sync 會自動下載 Gradle
   wrapper 與 SDK。
   - 想用命令列而非 IDE?先用本機 Gradle 8.9 跑一次 `gradle wrapper` 產生 wrapper,
     再 `./gradlew assembleDebug`。
2. 在實機或模擬器(Android 8.0+)執行。
3. 首次啟動點**前往開啟通知存取**,在系統清單裡把 NotiPeek 打開。
4. 傳一則 LINE(或其他)訊息給自己 —— 它會出現在 NotiPeek,而在 LINE 裡仍是**未讀**。

> `local.properties`(你的 SDK 路徑)與 Gradle wrapper JAR 不會被 commit,
> Android Studio 會自動補上。

## 專案結構

```
app/src/main/java/com/notipeek/
├─ NotiPeekApp.kt                     Application;持有 repository
├─ data/                             Room:entity / DAO / database / repository
│  ├─ CapturedMessage.kt             一則已存訊息(含去重 key)
│  ├─ MessageDao.kt                  查詢 + 每 App 彙總
│  ├─ AppDatabase.kt
│  └─ MessageRepository.kt
├─ service/
│  └─ PeekNotificationListener.kt    核心:攔截 + MessagingStyle 解析
└─ ui/
   ├─ MainActivity.kt                Compose 畫面(首頁 / 對話)
   ├─ MessageViewModel.kt
   └─ Theme.kt
```

## 可攜性 / 搬遷注意事項

- 純 AndroidX/Jetpack,無廠商 SDK —— 在任何能跑 Android Studio 的機器
  (Windows / macOS / Linux)clone 即可建置。
- 所有狀態都是裝置上的本地 Room DB(`notipeek.db`),不同步、不上傳。換新手機會從頭
  開始(刻意如此 —— 訊息只留在收到它的那支手機)。
- 只有通知監聽元件是由系統依名稱註冊實例化;release 版用 ProGuard 規則保留它。

## 使用倫理

請只用在**自己的手機、自己的訊息**上。未經同意讀取他人私人對話,在某些司法管轄區
可能違法。

## 授權

MIT License,詳見 [LICENSE](LICENSE)。
