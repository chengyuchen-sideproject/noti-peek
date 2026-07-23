# Google Play Console — 上架資料參考 (NotiPeek)

貼進 Play Console 對應欄位即可。定位刻意走「**本地個人通知歷史**」(誠實且降被拒率),
不以「規避已讀」為主打。

======================================================================
## 1. Store listing / 商店資訊
======================================================================

### App name (≤30 chars)
NotiPeek

### English — Short description (≤80 chars)
A private, on-device history of your notifications. No internet, no accounts.

### English — Full description (≤4000 chars)
NotiPeek keeps a private, on-device history of the notifications your phone
receives, so you never lose a message you glanced at and dismissed.

Once you grant notification access, NotiPeek quietly saves each incoming
notification — the text, who it's from, which app, and when — into a local
history you can scroll back through any time. Everything stays on your phone.

WHY PEOPLE USE IT
• Recover a notification you swiped away by accident.
• Keep a searchable log of messages from your messaging and other apps.
• Read a message from the notification without opening the app.

PRIVACY FIRST
• The app declares NO internet permission — your data is technically unable to
  leave your device.
• No servers, no cloud, no accounts, no analytics, no ads, no third-party SDKs.
• Notifications are stored only in a private local database. Clear them any time,
  per app or all at once; uninstalling deletes everything.

HOW IT WORKS
NotiPeek registers a NotificationListenerService. After you enable "Notification
access" for it in system settings, Android delivers a copy of each posted
notification to the app, which stores it locally. Reading a notification this way
is a system-level action, so it does not open the source chat.

GOOD TO KNOW (inherent limits, not bugs)
• Only notifications that arrive while access is granted are captured — there is
  no way to fetch history from before that.
• Stickers, images and voice usually appear as placeholder text.
• On Xiaomi/MIUI and some other brands you must also enable Autostart and set the
  battery to "No restrictions" so the listener survives in the background; the app
  shows a card that links you straight to those settings.

USE RESPONSIBLY
Use NotiPeek on your own device, for your own notifications.

Open source: https://github.com/chengyuchen-sideproject/noti-peek

### 繁體中文 (zh-TW) — Short description (≤80 chars)
私密、存在本機的通知歷史。無網路、無帳號、資料不外流。

### 繁體中文 (zh-TW) — Full description (≤4000 chars)
NotiPeek 幫你把手機收到的通知,保存成一份**私密、存在本機**的歷史,讓你不會弄丟
那些瞄過一眼就滑掉的訊息。

授予通知存取權後,NotiPeek 會把每則進來的通知——內容、來自誰、哪個 App、什麼時間
——默默存進本機歷史,隨時往回捲都看得到。所有資料都留在你的手機裡。

為什麼有人需要它
• 找回不小心滑掉的通知。
• 為各 App(含通訊軟體)的訊息留一份可回顧的紀錄。
• 直接從通知讀訊息,不必打開該 App。

隱私優先
• App **未宣告網路權限**——資料技術上根本無法離開你的裝置。
• 無伺服器、無雲端、無帳號、無分析、無廣告、無第三方 SDK。
• 通知只存在私有的本機資料庫。可隨時清除(單一 App 或全部);解除安裝即刪除全部。

運作方式
NotiPeek 註冊一個 NotificationListenerService。你在系統設定為它開啟「通知存取」後,
Android 會把每則通知的副本交給 App 存到本機。以這種方式讀通知是系統層動作,不會打開
來源聊天室。

須知(機制天生限制,非 bug)
• 只擷取「授權後」到達的通知——之前的抓不到。
• 貼圖、圖片、語音通常只顯示佔位文字。
• 小米/MIUI 及部分品牌需另外開「自啟動」+ 電池「無限制」,監聽才能在背景存活;App 內
  會有引導卡直接帶你到那些設定。

請負責任地使用
請在你自己的裝置、對你自己的通知使用 NotiPeek。

開源:https://github.com/chengyuchen-sideproject/noti-peek

### Category / 類別
Tools(工具) 或 Productivity(生產力)。建議 Tools。

### Privacy Policy URL
https://github.com/chengyuchen-sideproject/noti-peek/blob/master/PRIVACY.md
(若要更乾淨的網址,可開 GitHub Pages;blob 網址 Play 也接受。)

### Graphics 需準備(尺寸)
• App icon: 512×512 PNG(用現有 ic_notipeek 放大導出)
• Feature graphic: 1024×500 PNG
• Phone screenshots: 至少 2 張(建議 4–8;可用實機截圖,已有首頁/設定頁)

======================================================================
## 2. Data safety form / 資料安全表單(逐題答案)
======================================================================
• Does your app collect or share any of the required user data types?
  → NO(App 無網路、資料不外流,選「不收集、不分享」)
• Is all of the user data encrypted in transit? → N/A(無傳輸)
• Do you provide a way for users to request that their data is deleted?
  → YES — 使用者可在 App 內清除(per-app / all),或解除安裝即全刪。
• Data collected / shared 清單 → 全部留空(none)。

備註:雖然 App 會「讀取通知」,但因為**只存在本機、不傳輸也不分享**,Data Safety
的定義下屬於「不收集」(data stays on device)。仍要在說明據實描述本機儲存。

======================================================================
## 3. 敏感權限 / 通知存取聲明(App content → Sensitive permissions)
======================================================================
若被要求說明使用 Notification Listener 的理由,可用:

"NotiPeek's core feature is a personal, on-device history of the user's own
notifications. The NotificationListenerService is the only Android mechanism that
delivers posted notifications to the app so they can be saved to a local database
for the user to review later. No notification data is transmitted off the device
(the app declares no INTERNET permission); it is neither shared nor sold."

======================================================================
## 4. 內容分級 / Content rating
======================================================================
問卷據實回答:無暴力、無性、無賭博、無使用者生成內容分享(資料不外流)。
預期分級:Everyone / 3+。

======================================================================
## 5. 新個人帳號的測試門檻(重要)
======================================================================
2023/11 後註冊的個人開發者帳號:須先跑**封閉測試(Closed testing)**,至少
**12 名測試者、持續 14 天**,才能申請正式(Production)上架。
→ 建議先把 AAB 上到 Closed testing track,找滿 12 人掛 14 天。
