import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BusinessDashboard, BusinessMenuItem, BusinessOrder, BusinessStaffItem } from '../models/api.models';

const API_BASE_URL = 'https://kbook.iadv.cloud/api/v1';

@Injectable({ providedIn: 'root' })
export class BusinessApiService {
  private readonly http = inject(HttpClient);

  getDashboard() {
    return this.http.get<BusinessDashboard>(`${API_BASE_URL}/business/dashboard`);
  }

  getOrders() {
    return this.http.get<BusinessOrder[]>(`${API_BASE_URL}/business/orders`);
  }

  getMenu() {
    return this.http.get<BusinessMenuItem[]>(`${API_BASE_URL}/business/menu`);
  }

  getStaff() {
    return this.http.get<BusinessStaffItem[]>(`${API_BASE_URL}/business/staff`);
  }
}
