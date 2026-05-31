export interface AuthSession {
  token: string;
  refreshToken: string;
  expiresIn: number;
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
