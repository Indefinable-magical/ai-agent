declare namespace API {
  type BaseResponseBoolean = {
    code?: number;
    data?: boolean;
    message?: string;
  };

  type BaseResponseLoginUserVO = {
    code?: number;
    data?: LoginUserVO;
    message?: string;
  };

  type BaseResponseLong = {
    code?: number;
    data?: number;
    message?: string;
  };

  type BaseResponsePageUserVO = {
    code?: number;
    data?: PageUserVO;
    message?: string;
  };

  type BaseResponsePageStockInfo = {
    code?: number;
    data?: PageStockInfo;
    message?: string;
  };

  type BaseResponseString = {
    code?: number;
    data?: string;
    message?: string;
  };

  type BaseResponseUserPreferenceVO = {
    code?: number;
    data?: UserPreferenceVO;
    message?: string;
  };

  type BaseResponseStockUserPreferenceVO = {
    code?: number;
    data?: StockUserPreferenceVO;
    message?: string;
  };

  type BaseResponseStockWatchlistVOArray = {
    code?: number;
    data?: StockWatchlistVO[];
    message?: string;
  };

  type BaseResponseUser = {
    code?: number;
    data?: User;
    message?: string;
  };

  type BaseResponseUserVO = {
    code?: number;
    data?: UserVO;
    message?: string;
  };

  type DeleteRequest = {
    id?: number;
  };

  type doChatWithManusParams = {
    message: string;
  };

  type doChatWithStockAppServerSentEventParams = {
    message: string;
    chatId: string;
  };

  type doChatWithStockAppServerSseEmitterParams = {
    message: string;
    chatId: string;
  };

  type doChatWithStockAppSSEParams = {
    message: string;
    chatId: string;
  };

  type doChatWithStockAppSyncParams = {
    message: string;
    chatId: string;
  };

  type getUserByIdParams = {
    id: number;
  };

  type getUserVOByIdParams = {
    id: number;
  };

  type LoginUserVO = {
    id?: number;
    userAccount?: string;
    userName?: string;
    userAvatar?: string;
    userProfile?: string;
    userRole?: string;
    userStatus?: number;
    createTime?: string;
    updateTime?: string;
  };

  type OrderItem = {
    column?: string;
    asc?: boolean;
  };

  type PageUserVO = {
    records?: UserVO[];
    total?: number;
    size?: number;
    current?: number;
    orders?: OrderItem[];
    optimizeCountSql?: PageUserVO;
    searchCount?: PageUserVO;
    optimizeJoinOfCountSql?: boolean;
    maxLimit?: number;
    countId?: string;
    pages?: number;
  };

  type PageStockInfo = {
    records?: StockInfo[];
    total?: number;
    size?: number;
    current?: number;
    orders?: OrderItem[];
    optimizeCountSql?: PageStockInfo;
    searchCount?: PageStockInfo;
    optimizeJoinOfCountSql?: boolean;
    maxLimit?: number;
    countId?: string;
    pages?: number;
  };

  type ServerSentEventString = true;

  type SseEmitter = {
    timeout?: number;
  };

  type User = {
    id?: number;
    userAccount?: string;
    userPassword?: string;
    userName?: string;
    userAvatar?: string;
    userProfile?: string;
    userRole?: string;
    userStatus?: number;
    editTime?: string;
    createTime?: string;
    updateTime?: string;
    isDelete?: number;
  };

  type UserAddRequest = {
    userName?: string;
    userAccount?: string;
    userAvatar?: string;
    userProfile?: string;
    userRole?: string;
    userStatus?: number;
  };

  type UserLoginRequest = {
    userAccount?: string;
    userPassword?: string;
  };

  type UserQueryRequest = {
    pageNum?: number;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
    id?: number;
    userName?: string;
    userAccount?: string;
    userProfile?: string;
    userRole?: string;
    userStatus?: number;
  };

  type UserRegisterRequest = {
    userAccount?: string;
    userPassword?: string;
    checkPassword?: string;
  };

  type UserProfileUpdateRequest = {
    userName?: string;
    userAvatar?: string;
    userProfile?: string;
    oldPassword?: string;
    newPassword?: string;
    checkPassword?: string;
  };

  type UserPreferenceUpdateRequest = {
    theme?: string;
    defaultAiApp?: string;
    defaultRiskPreference?: string;
    conversationDensity?: string;
  };

  type UserUpdateRequest = {
    id?: number;
    userName?: string;
    userAvatar?: string;
    userProfile?: string;
    userRole?: string;
    userStatus?: number;
  };

  type UserVO = {
    id?: number;
    userAccount?: string;
    userName?: string;
    userAvatar?: string;
    userProfile?: string;
    userRole?: string;
    userStatus?: number;
    createTime?: string;
  };

  type UserPreferenceVO = {
    theme?: string;
    defaultAiApp?: string;
    defaultRiskPreference?: string;
    defaultRiskPreferenceName?: string;
    conversationDensity?: string;
  };

  type StockWatchlistAddRequest = {
    stockCode?: string;
    stockName?: string;
    remark?: string;
  };

  type StockWatchlistUpdateRequest = {
    id?: number;
    stockCode?: string;
    stockName?: string;
    remark?: string;
  };

  type StockRiskPreferenceRequest = {
    riskPreference?: string;
  };

  type StockInfo = {
    id?: number;
    stockCode?: string;
    stockName?: string;
    market?: string;
    industry?: string;
    themes?: string;
    riskTags?: string;
    reason?: string;
    hotScore?: number;
    createTime?: string;
    updateTime?: string;
    isDelete?: number;
  };

  type StockInfoAddRequest = {
    stockCode?: string;
    stockName?: string;
    market?: string;
    industry?: string;
    themes?: string;
    riskTags?: string;
    reason?: string;
    hotScore?: number;
  };

  type StockInfoUpdateRequest = {
    id?: number;
    stockCode?: string;
    stockName?: string;
    market?: string;
    industry?: string;
    themes?: string;
    riskTags?: string;
    reason?: string;
    hotScore?: number;
  };

  type StockInfoQueryRequest = {
    pageNum?: number;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
    stockCode?: string;
    stockName?: string;
    market?: string;
    industry?: string;
    themeKeyword?: string;
    riskKeyword?: string;
    reasonKeyword?: string;
  };

  type StockWatchlistVO = {
    id?: number;
    stockCode?: string;
    stockName?: string;
    remark?: string;
    createTime?: string;
  };

  type StockUserPreferenceVO = {
    riskPreference?: string;
    riskPreferenceName?: string;
  };
}
