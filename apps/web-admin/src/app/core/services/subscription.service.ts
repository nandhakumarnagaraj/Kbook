import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  SubscriptionPlan,
  SubscriptionSummary,
  ChangePlanRequest,
  AdminSubscriptionOverrideRequest
} from '../models/subscription.model';

const API_BASE_URL = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly http = inject(HttpClient);

  /**
   * Get current merchant subscription summary, limits, and usage.
   */
  getSubscriptionSummary(): Observable<SubscriptionSummary> {
    return this.http.get<SubscriptionSummary>(`${API_BASE_URL}/business/subscription/summary`);
  }

  /**
   * List all available plans for upgrade or review.
   */
  getAvailablePlans(): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${API_BASE_URL}/business/subscription/plans`);
  }

  /**
   * Switch or upgrade subscription plan.
   */
  changePlan(request: ChangePlanRequest): Observable<SubscriptionSummary> {
    return this.http.post<SubscriptionSummary>(`${API_BASE_URL}/business/subscription/change-plan`, request);
  }

  /**
   * Superadmin: get restaurant subscription details.
   */
  getAdminSubscription(restaurantId: number): Observable<SubscriptionSummary> {
    return this.http.get<SubscriptionSummary>(`${API_BASE_URL}/admin/subscriptions/${restaurantId}`);
  }

  /**
   * Superadmin: override restaurant subscription plan or status.
   */
  overrideAdminSubscription(
    restaurantId: number,
    request: AdminSubscriptionOverrideRequest
  ): Observable<SubscriptionSummary> {
    return this.http.post<SubscriptionSummary>(
      `${API_BASE_URL}/admin/subscriptions/${restaurantId}/override`,
      request
    );
  }
}
