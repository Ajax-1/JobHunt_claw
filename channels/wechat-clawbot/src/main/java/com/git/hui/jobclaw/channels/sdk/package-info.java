/**
 * Weixin ClawBot SDK - Complete Java implementation of the Weixin iLink Bot API.
 * 
 * <h2>Overview</h2>
 * This package provides a complete SDK for interacting with the Weixin (WeChat) iLink Bot API,
 * enabling Java applications to build WeChat bots with full messaging capabilities.
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>Message Receiving:</b> Long-polling based message retrieval via getUpdates API</li>
 *   <li><b>Text Messaging:</b> Send plain text messages with context tracking</li>
 *   <li><b>Media Messaging:</b> Send images, videos, and file attachments with AES-128-ECB encryption</li>
 *   <li><b>Voice Support:</b> Handle voice messages with optional speech-to-text</li>
 *   <li><b>Typing Indicators:</b> Show/hide "typing" status during processing</li>
 *   <li><b>Context Management:</b> Automatic context_token management with disk persistence</li>
 *   <li><b>CDN Integration:</b> Complete CDN upload workflow with encryption</li>
 * </ul>
 * 
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create SDK instance
 * WeixinSdk sdk = new WeixinSdk.Builder()
 *     .baseUrl("https://ilinkai.weixin.qq.com")
 *     .cdnBaseUrl("https://novac2c.cdn.weixin.qq.com/c2c")
 *     .token("your-bot-token")
 *     .accountId("my-account")
 *     .build();
 * 
 * // Start polling for messages
 * sdk.startPolling(message -> {
 *     String text = MessageBuilder.extractText(message);
 *     String fromUser = message.getFromUserId();
 *     String contextToken = message.getContextToken();
 *     
 *     // Reply to message
 *     try {
 *         sdk.getMessageSender().sendTextMessage(fromUser, "Hello!", contextToken);
 *     } catch (Exception e) {
 *         e.printStackTrace();
 *     }
 * });
 * }</pre>
 * 
 * <h2>Architecture</h2>
 * The SDK is organized into several key components:
 * <ul>
 *   <li>{@link ai.javaclaw.channels.weclawbot.sdk.WeixinSdk} - Main entry point, orchestrates all components</li>
 *   <li>{@link ai.javaclaw.channels.weclawbot.sdk.WeixinApiClient} - Low-level HTTP API client</li>
 *   <li>{@link ai.javaclaw.channels.weclawbot.sdk.MessageSender} - High-level message sending operations</li>
 *   <li>{@link ai.javaclaw.channels.weclawbot.sdk.MessageBuilder} - Message construction utilities</li>
 *   <li>{@link ai.javaclaw.channels.weclawbot.sdk.CdnUploader} - CDN upload with AES encryption</li>
 *   <li>{@link ai.javaclaw.channels.weclawbot.sdk.ContextTokenManager} - Context token storage and persistence</li>
 *   <li>{@link ai.javaclaw.channels.weclawbot.sdk.WeixinTypes} - All API types and constants</li>
 * </ul>
 * 
 * <h2>Protocol Details</h2>
 * The SDK implements the complete Weixin iLink Bot protocol:
 * <ul>
 *   <li>Authentication via Bearer token in Authorization header</li>
 *   <li>X-WECHAT-UIN header with random uint32 base64-encoded</li>
 *   <li>iLink-App-Id and iLink-App-ClientVersion headers</li>
 *   <li>Long-polling getUpdates with configurable timeout</li>
 *   <li>AES-128-ECB encryption for media uploads</li>
 *   <li>Context token echo for message continuity</li>
 * </ul>
 * 
 * <h2>Error Handling</h2>
 * Common error codes:
 * <ul>
 *   <li>40001, 40014, 42001 - Session expired, requires re-authentication</li>
 *   <li>-14 - Session timeout</li>
 *   <li>Other HTTP errors are propagated as exceptions</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * All SDK components are thread-safe. The polling mechanism runs in a background daemon thread.
 * Multiple SDK instances can be created for multi-account scenarios.
 * 
 * <h2>References</h2>
 * <ul>
 *   <li>Frontend implementation: plugins/weclawbot/docs/package/src/</li>
 *   <li>API documentation: plugins/weclawbot/docs/package/README.zh_CN.md</li>
 *   <li>Example usage: {@link ai.javaclaw.channels.weclawbot.sdk.WeixinSdkExample}</li>
 * </ul>
 * 
 * @see ai.javaclaw.channels.weclawbot.sdk.WeixinSdk
 * @see ai.javaclaw.channels.weclawbot.sdk.WeixinApiClient
 * @see ai.javaclaw.channels.weclawbot.sdk.MessageSender
 */
package com.git.hui.jobclaw.channels.sdk;
