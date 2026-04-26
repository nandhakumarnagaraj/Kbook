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
}
