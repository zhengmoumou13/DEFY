# Security policy / 安全问题

当前维护对象为仓库最新 `main` 和最新公开版本，没有已承诺的商业支持或响应时限。

## 私下报告

请使用仓库 **Security → Report a vulnerability**：

[提交私密安全报告](https://github.com/zhengmoumou13/DEFY/security/advisories/new)

报告中提供版本、影响范围、最小复现和修复建议即可。不要在公开 Issue 中发布可利用细节、API Key、签名密钥、个人数据库或健康记录。如果该入口不可用，可在普通 Issue 中仅请求建立私密联系渠道，不附敏感细节。

## 凭据与导出

- API Key 在设备本地加密，不进入正常导出的备份；这不意味着整个数据库或 `.dwb` 已加密。
- 只有用户主动使用对应 AI 功能时才应发送相关数据。报告不符合此边界的行为。
- 意外公开了 Key 时，应在服务商处撤销 / 轮换；删除一条 Git 提交不能保证外部副本消失。
- 签名私钥不得提交，正式构建由分发者保管自己的签名身份。

隐私及权限说明见 [docs/PRIVACY.md](docs/PRIVACY.md)。
