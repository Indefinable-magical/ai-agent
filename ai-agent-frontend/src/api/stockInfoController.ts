import request from '@/request'

export async function addStockInfo(
  body: API.StockInfoAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong>('/admin/stock-pool/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

export async function updateStockInfo(
  body: API.StockInfoUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/admin/stock-pool/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

export async function deleteStockInfo(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/admin/stock-pool/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

export async function listStockInfoByPage(
  body: API.StockInfoQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageStockInfo>('/admin/stock-pool/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
