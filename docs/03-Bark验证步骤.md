# Bark 验证步骤

## iPhone

1. 在 iPhone 安装 Bark。
2. 打开 Bark，复制首页推送 URL 里的 key。
3. 不要复制 iOS Device Token。Device Token 通常是 64 位十六进制字符串，Bark `/push` 接口不能直接使用。
4. 先用 curl 测试 Bark：

```bash
curl -X POST https://api.day.app/push \
  -H "Content-Type: application/json" \
  -d '{"device_key":"YOUR_BARK_KEY","title":"fkwk-push test","body":"Bark is working","group":"fkwk","level":"active"}'
```

如果 iPhone 能收到，说明 Bark 链路正常。

## Android App

在设置页填写：

```text
Bark 服务器地址：https://api.day.app
Bark Key：Bark 首页 URL 里的 key
标题格式：{app}-{title}
```

标题模板支持：

```text
{app}      来源应用名
{title}    原通知标题
{package}  来源包名
```

提醒级别映射：

```text
仅通知中心：Bark passive
横幅弹窗：Bark active
时效性通知：Bark timeSensitive
```

## 验证通知捕获

1. 在 App 页勾选要监听的应用。
2. 给 Android 设备发送一条真实通知。
3. 查看通知历史。

判断方式：

- 历史里没有记录：Android 没有捕获到，检查通知读取权限和 App 是否真的弹系统通知
- 历史里显示已屏蔽：命中了关键词屏蔽
- 历史里显示已跳过：命中了亮屏暂停策略
- 历史里显示失败：检查 Bark key、网络、Bark server URL
- 历史里显示已转发但 iPhone 没响：检查 iPhone Bark 通知权限、专注模式、Bark App 设置

## Optional Icons

如果部署了可选图标服务，可以在设置页打开 Bark 图标：

```text
图标基础地址：https://your-domain.example/icons
图标上传 Token：server/.env 里的 ICON_UPLOAD_TOKEN
```

勾选应用时，Android 会尝试上传该应用图标。之后 Bark 推送会携带 `icon` 字段。
