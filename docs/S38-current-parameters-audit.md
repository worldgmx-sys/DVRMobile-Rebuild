# S38 设置回读与高亮核验

本轮核对全部 73 个设置目录项与原厂 CGI_PROCESS.sh GET 分支，并结合前序 FIFO/HDR/MCU 反汇编结论。此表描述接口和实现证据，不代表所有功能已通过实机验证。

高亮为设备响应中的参数值；未知、无接口、命令回显、HTTP 首页、错误响应均不选中。数值输入保留独立待发送值，设备回读另列高亮。未知枚举仅显示原始值，不猜测 0/1 与 ON/OFF 的映射。原厂循环 GET 使用 VideoClipTime 键，而 SET 写入 LoopingVideo 键，即使修复命令回显，也需要检查键不一致。

## 关键调用链限制

- setbitrate：FIFO 需要 channel 和 bps，1920/1280/SD 分辨率存在不同限幅；现有 CGI 桥接不足以可靠传入两个参数。
- VideoQuality：quality 回调仅打印 TBD；MotionVideoTime 同样是占位，MotionDetect 没有对应注册项。展示配置并不证明功能执行。
- HDR：存在事件和重建录像管线调用链，但默认 HDR IQ/NE 调校文件缺失；当前不开放写入。
- ParkingMonitor：park 修改 SOC 本地值；MCU 停车事件与录像命令确认另有状态机，不等同于录制文件关闭完成。
- 网络、时间、屏幕、图像枚举：保留原目录和原限制，未将存在 SET 分支标为实机有效。
- 维护、升级、格式化、重启：属于操作命令，不存在可选择的“当前参数”；保留独立入口及原确认流程。

## 完整设置目录

| 功能 | SET 属性 | GET 属性 | 原厂脚本读取证据 |
|---|---|---|---|
| 视频分辨率 | VideoRes | Videores | 返回命令文本（不能高亮） |
| 循环录像时长 | LoopingVideo | VideoClipTime | 返回命令文本（不能高亮） |
| 视频质量 | VideoQuality | VideoQuality | 返回命令文本（不能高亮） |
| 视频码率 | setbitrate | — | 无 GET 分支 |
| 自动录像 | AutoRec | AutoRec | 返回命令文本（不能高亮） |
| 预录 | VideoPreRecord | VideoPreRecord | 返回命令文本（不能高亮） |
| 延时录像 | Timelapse | Timelapse | 返回命令文本（不能高亮） |
| 慢动作 | SlowMotion | SlowMotion | 返回命令文本（不能高亮） |
| 自动停止录像时间 | VideoOffTime | VideoOffTime | 返回命令文本（不能高亮） |
| 照片分辨率 | ImageRes | — | 无 GET 分支 |
| 连拍数量 | StillBurstShot | StillBurstShot | 返回命令文本（不能高亮） |
| 亮度 | Brightness | Brightness | 返回命令文本（不能高亮） |
| 对比度 | Contrast | Contrast | 返回命令文本（不能高亮） |
| 色相 | Hue | Hue | 返回命令文本（不能高亮） |
| 饱和度 | Saturation | Saturation | 返回命令文本（不能高亮） |
| 锐度 | Sharpness | Sharpness | 返回命令文本（不能高亮） |
| Gamma | Gamma | Gamma | 返回命令文本（不能高亮） |
| 曝光补偿 | EV | EV | 返回命令文本（不能高亮） |
| 自动曝光 | AE | AE | 返回命令文本（不能高亮） |
| ISO | ISO | — | 无 GET 分支 |
| 图像效果 | Effect | — | 无 GET 分支 |
| 防闪烁 | Flicker | Flicker | 返回命令文本（不能高亮） |
| 白平衡 | AWB | AWB | 返回命令文本（不能高亮） |
| 快门 | Shutter | Shutter | 返回命令文本（不能高亮） |
| HDR | HDR | HDR | 返回命令文本（不能高亮） |
| 夜间模式 | NightMode | NightMode | 返回命令文本（不能高亮） |
| 录像录音 | SoundRecord | SoundRecord | 返回命令文本（不能高亮） |
| 麦克风灵敏度 | MicSensitivity | MicSensitivity | 返回命令文本（不能高亮） |
| 风噪抑制 | WNR | WNR | 返回命令文本（不能高亮） |
| 播放音量 | PlaybackVolume | PlaybackVolume | 返回命令文本（不能高亮） |
| 提示音 | Beep | Beep | 返回命令文本（不能高亮） |
| 语音提示 | VoiceSwitch | — | 无 GET 分支 |
| 停车监控 | ParkingMonitor | ParkingMonitor | 返回命令文本（不能高亮） |
| 行车碰撞检测 | GSensor | GSensor | 返回命令文本（不能高亮） |
| 移动侦测 | MotionDetect | MotionDetect | 返回命令文本（不能高亮） |
| 移动侦测录像时长 | MotionVideoTime | MotionVideoTime | 返回命令文本（不能高亮） |
| 车道偏离预警 | LDWS | LDWS | 返回命令文本（不能高亮） |
| 前车碰撞预警 | FCWS | FCWS | 返回命令文本（不能高亮） |
| 前车起步提醒 | SAG | SAG | 返回命令文本（不能高亮） |
| GPS 水印 | GpsStamp | GpsStamp | 返回命令文本（不能高亮） |
| 速度水印 | SpeedStamp | SpeedStamp | 返回命令文本（不能高亮） |
| 录像水印 | RecStamp | — | 无 GET 分支 |
| 日期与徽标水印 | DateLogoStamp | DateLogoStamp | 返回命令文本（不能高亮） |
| 时间日期格式 | DateTimeFormat | DateTimeFormat | 返回命令文本（不能高亮） |
| 速度单位 | SpeedUint | — | 无 GET 分支 |
| 测速摄像头提醒 | SpeedCamAlert | — | 无 GET 分支 |
| 超速提醒 | SpeedLimitAlert | — | 无 GET 分支 |
| 热点名称 SSID | Net.WIFI_AP.SSID | Net.WIFI_AP.SSID | 执行查询；须解析有效值 |
| 热点密码 | Net.WIFI_AP.CryptoKey | Net.WIFI_AP.CryptoKey | 不读取或展示密码 |
| STA 目标网络名称 | Net.WIFI_STA.AP.2.SSID | Net.WIFI_STA.AP.2.SSID | 执行查询；须解析有效值 |
| STA 目标网络密码 | Net.WIFI_STA.AP.2.CryptoKey | Net.WIFI_STA.AP.2.CryptoKey | 不读取或展示密码 |
| AP/STA 模式切换 | Net.WIFI_STA.AP.Switch | Net.WIFI_STA.AP.Switch | 无输出 |
| 停车唤醒 G-sensor | PowerOnGSensor | PowerOnGSensor | 返回命令文本（不能高亮） |
| 设置 MJPEG 时间戳 | Camera.Preview.MJPEG.TimeStamp | Camera.Preview.MJPEG.TimeStamp | 执行查询；须解析有效值 |
| 网络重置 | Net | — | 无 GET 分支 |
| 设备录像控制 | Video | — | 无 GET 分支 |
| 关机/供电控制 | Camera.System.Power | — | 无 GET 分支 |
| 恢复出厂设置 | FactoryReset | — | 无 GET 分支 |
| 重启设备 | reboot | — | 无 GET 分支 |
| SD 卡格式化 | SD0 | — | 无 GET 分支 |
| 重新加载录像配置 | Setting | — | 无 GET 分支 |
| 位置数据新增 | PosSetting_Add | — | 无 GET 分支 |
| 删除最后一条位置数据 | PosSetting_DelLast | — | 无 GET 分支 |
| 清空位置数据 | PosSetting_DelAll | — | 无 GET 分支 |
| 系统重启另一接口 | RebootSystem | — | 无 GET 分支 |
| 语言 | Language | Language | 返回命令文本（不能高亮） |
| LCD 亮度 | LCDBrightness | — | 无 GET 分支 |
| LCD 节电 | LcdPowerSave | LcdPowerSave | 返回命令文本（不能高亮） |
| 自动关机 | AutoPowerOff | AutoPowerOff | 返回命令文本（不能高亮） |
| USB 模式 | UsbFunction | UsbFunction | 返回命令文本（不能高亮） |
| 时区 | TimeZone | TimeZone | 返回命令文本（不能高亮） |
| 同步时间 | SyncTime | — | 无 GET 分支 |
| 手动时间 | TimeSettings | — | 无 GET 分支 |

## 实现与验证

查询已有 getter（排除密码），最多四项并行，每项总超时四秒；进入高级设置、连接变化或点击刷新时读取。写入后清除旧高亮并回读，读回不同值时高亮读回值，不能把请求值标为已应用。换 IP 清空数据，读取与写入串行避免旧结果覆盖。

新增解析测试覆盖命令回显、HTML 首页、正常键值/XML、多个 Value、错误状态、未知枚举、反向布尔编码和密码排除。Android 编译和测试在 PR 构建中执行；无记录仪连接，设备验证尚未进行。
