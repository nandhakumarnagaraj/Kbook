export interface AuthSession {
  token: string;
  restaurantId: number | null;
  userName: string;
  loginId: string;
  userEmail: string | null;
  whatsappNumber: string | null;
  role: string;
}

export interface LoginRequest {
  loginId: string;
  password: string;
  /** Tells the server this login is from the web-admin surface; the server
   * rejects SHOP_STAFF accounts at the web surface (they are POS-only). */
  surface?: 'web';
}
