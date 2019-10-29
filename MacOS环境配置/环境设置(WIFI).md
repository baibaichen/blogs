#Mac os 环境设置

1. 办公工具【由 self service 安装】

   - [x] 微信（~~需要创建APPID~~，使用她人的）
   - [x] 搜狗拼音
   - [x] Adobe PDF
   - [x] office 套件
   - [x] 五楼打印机
   - [x] Chrome
2. 系统工具
   - [x] Brew
   - [x] ShadowSocks**NG**【PAC 文件】
         - [ ] https://lvii.gitbooks.io/outman/content/ss.mac.html
3. 开发工具 `由Brew安装`
   - [x] java
         - [x] 使用jenv支持多个java版本
               1. http://davidcai.github.io/blog/posts/install-multiple-jdk-on-mac/
               2. http://www.jenv.be/  注意一定要改 .bash_profile
               3. jenv java_home https://stackoverflow.com/questions/28615671/set-java-home-to-reflect-jenv-java-version
               4. jenv 和mvn https://stackoverflow.com/questions/30602633/maven-ignoring-jenv-settings
   - [x] Python，取代系统的
   - [x] Smart git
   - [x] Idea
   - [x] `mvn`
   - [x] `gradle`
   - [x] `Sublime Text`
         - [x] Package Control
         - [x] SFTP
         - [x] SideBar Enhancement
         - [x] SQL Beautifier
         - [x] 主题 Soda
   - [x] `Typora`
         - [x] Cat fish
   - [x] Beyond Compare （当前的版本是4.2.3）
         - [ ] [Beyond Compare Pro for Mac 无限试用](http://log.yanwen.org/beyond-compare-pro-for-mac-4.0.2-po-jie-wu-xian-shi-yong)
         - [ ] [Beyond Compare for mac 无限试用方法](http://www.qiangblog.com/post/mac/beyond-compare-for-mac-wu-xian-shi-yong-fang-fa)
4. 环境设置
   1. Smart-Git
      - [x] 配置github
      - [x] 配置 `open in terminal`，在ITerm2中打开。
   2. MVN
      - [x] 设置代理，使用socks5
      - [x] SPARK
      - [x] Realtime-ETL
      - [x] OOZIE
      - [x] Zepplin【用代理无法下载nodejs】
      - [ ] HIVE
   3. Gradle
      - [x] kafka
5. Wifi开发环境设置
   - [x] 远程终端工具（~~寻找xShell的~~，暂定**ITerm2**）
         - [x] Shell的选择（**bash** or ~~zsh~~）
   - [ ] 配置iTerm2
         - [x] 配置[动态Badges](https://www.iterm2.com/documentation-badges.html)
         - [ ] [让 iTerm2 支持拖拽上传文件和右键下载文件](http://jk2k.com/2016/03/iTerm2-enable-shell-integration-to-support-file-uploads-and-downloads/)，就是**Shell Integration**
         - [x] iTerm2中遇到这个问题 [Terminal prompt not wrapping correctly](https://unix.stackexchange.com/questions/105958/terminal-prompt-not-wrapping-correctly)，还没有找到解决方法
         - [ ] [配置rz和sz](https://molunerfinn.com/iTerm2-lrzsz/#%E9%80%9A%E8%BF%87brew%E5%AE%89%E8%A3%85lrzsz)，不能使用自动登录的脚本
   - [x] 配置IDEA的key mapping
         - [x] [IDEA的License Server](http://blog.lanyus.com/archives/314.html)，注意在host里加上 `0.0.0.0 account.jetbrains.com`
   - [x] 配置gitlab sh
   - [ ] 配置gitlab sg 
   - [ ] Office-VPN
   - [x] IDC-VPN
         - [x] 盛大云
         - [x] 金华
         - [x] uCloud
         - [x] [使用Expect 在==ITerm2==中自动登录](http://devzhao.com/2017/02/16/mac%E4%B8%8Aexpect%E8%87%AA%E5%8A%A8%E7%99%BB%E5%BD%95%E6%96%B9%E6%A1%88/)
               - [x] 金华
                     - [ ] 稳定性！！！
               - [x] 盛大云
                     - [x] Expect  搞定 `cd ~/chenchang`
               - [ ] uCloud
   - [x] SFTP
         - [x] guanggao44

# 快捷键

## 常见的编辑快捷

- [ ] 常见的编辑快捷键有哪些?

- [x] 快速调出ITerm2终端，⌥+Space。

      > [推荐使用 iTerm2，比自带的强大一些。iTerm2 -> Preferences -> Appearance -> Hotkey -> Check "Show/hide iTerm2 with a system-wide hotkey"](https://www.zhihu.com/question/20692634/answer/18009514)

## IntelliJ IDEA

>  现在**Visual Stduio**的快捷键导致：
>
> 1. 拷贝和粘贴仍然使用：**Ctrl+C** 和 **Ctrl+V**，但是系统使用**⌘+C**和**⌘+V**不一致
> 2. 某些快捷键和系统冲突，比如 **Ctrl+→** 和 **Ctrl+←**
- [x] 切换成Mac OSX 10.5 +，但是更改以下的快捷键到Visual Stduio
      - [x] Resume Program：F5
      - [x] Build Project：F7
      - [x] Step into：F8
      - [x] Taggle Line BreakPoint：F9
      - [x] Step OverF10
      - [x] ~~F11：和系统冲突~~
      - [x] Declaration：F12
      - [x] Find Usage：
      - [x] Back：以前是⌥+←，要想一个好一点的
      - [x] Forward：以前是⌥+→，要想一个好一点的

| 更新后的Mac  OS 10.5 + | 功能项                                      | Visual Stduio | 对应的 Mac OS 10.5  +                       | Mac OS 10.5 + |
| ------------------ | ---------------------------------------- | ------------- | ---------------------------------------- | ------------- |
| F5                 | Resume Program                           | F5            | Refactor/Copy…                           |               |
| F7                 | Build  Project                           | F7            | Step Into                                |               |
| F8                 | Step into                                | F8            | Step Over                                |               |
| Alt F8             | Force Step Into                          | Alt F8        | Evaluate Expression…                     |               |
| Shift F8           | Step Out                                 |               |                                          |               |
| F9                 | Taggle Line  BreakPoint                  | F9            | Resume Program                           |               |
| Shfit F9           | Taggle BreakPoint  Enabled               |               |                                          |               |
| F10                | Step Over                                | F10           |                                          |               |
| F12                | Declaration                              | F12           | Jump to Last Tool  Window                |               |
| ⌥F7                | Find Usage                               | Alt Shift F7  | Force Step Into                          | ⌥F7           |
| ⌥⌘←，⌘]             | Back                                     | ⌥+←           | 和系统有冲突                                   | ⌥⌘←，⌘]        |
| ⌥⌘←，⌘]             | Forward                                  | ⌥+→           | 和系统有冲突                                   | ⌥⌘←，⌘]        |
| ⌃ ⌥H               | Call Hierarchy                           | ⌃ ⌥H          | Call Hierarchy                           |               |
| ⌃ ⇧N               | Navigate\|File...                        | ⌃ ⇧N          | 无对应键                                     |               |
| ⌃ ⌥⇧N              | Navigate\|Symbol...                      | ⌃ ⌥⇧N         | 无对应键                                     |               |
| ⌃G                 | Navigate\|Line/Column...                 | ⌃G            | Edit\|Find\|Add Selection for Next Occurrence |               |
| ⌃Q                 | Quick Documentation                      | ⌃Q            | Scala\|Implicit Type                     | F1            |
| 无需快捷键              | UML Support\|Apply  Current Layout       |               |                                          |               |
| 无需快捷键              | Refactor\|Copy…                          |               |                                          |               |
| 无需快捷键              | Evaluate Expression…                     |               |                                          |               |
| 无需快捷键              | Jump to Last Tool  Window                |               |                                          |               |
| 无需快捷键              | Edit\|Find\|Add Selection for Next Occurrence |               |                                          |               |

# Git 不常用操作

1. [How to apply a git patch from one repository to another?](https://stackoverflow.com/questions/931882/how-to-apply-a-git-patch-from-one-repository-to-another)