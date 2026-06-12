// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 此处后端没有提供注释 GET /ai/manus/chat */
export async function doChatWithManus(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.doChatWithManusParams,
  options?: { [key: string]: any }
) {
  return request<API.SseEmitter>("/ai/manus/chat", {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /ai/stock_app/chat/server_sent_event */
export async function doChatWithStockAppServerSentEvent(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.doChatWithStockAppServerSentEventParams,
  options?: { [key: string]: any }
) {
  return request<API.ServerSentEventString[]>(
    "/ai/stock_app/chat/server_sent_event",
    {
      method: "GET",
      params: {
        ...params,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /ai/stock_app/chat/sse */
export async function doChatWithStockAppSse(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.doChatWithStockAppSSEParams,
  options?: { [key: string]: any }
) {
  return request<string[]>("/ai/stock_app/chat/sse", {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /ai/stock_app/chat/sse_emitter */
export async function doChatWithStockAppServerSseEmitter(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.doChatWithStockAppServerSseEmitterParams,
  options?: { [key: string]: any }
) {
  return request<API.SseEmitter>("/ai/stock_app/chat/sse_emitter", {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 GET /ai/stock_app/chat/sync */
export async function doChatWithStockAppSync(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.doChatWithStockAppSyncParams,
  options?: { [key: string]: any }
) {
  return request<string>("/ai/stock_app/chat/sync", {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
