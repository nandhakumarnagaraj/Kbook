import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationItem } from '../core/models/api.models';

/**
 * The flyout was inline in the business dashboard, where the only way to close it
 * was to click the bell again: no Escape, no click-outside. On a dashboard people
 * leave open all day that reads as a stuck panel, so those two are the specs that
 * matter here.
 */
describe('NotificationBellComponent', () => {
  let fixture: ComponentFixture<NotificationBellComponent>;
  let component: NotificationBellComponent;

  const notification = (id: number, isRead = false): NotificationItem => ({
    id,
    restaurantId: 1,
    title: `Alert ${id}`,
    message: `Message ${id}`,
    eventType: 'order',
    isRead,
    createdAt: Date.now(),
  });

  function mount(notifications: NotificationItem[] = [], unreadCount = 0): void {
    fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.componentRef.setInput('notifications', notifications);
    fixture.componentRef.setInput('unreadCount', unreadCount);
    fixture.detectChanges();
    component = fixture.componentInstance;
  }

  function click(el: Element): void {
    el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();
  }

  function bell(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button.icon-action-btn');
  }

  function flyout(): HTMLElement | null {
    return fixture.nativeElement.querySelector('.notif-flyout');
  }

  function escape(): void {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
  }

  afterEach(() => fixture?.destroy());

  it('opens on the bell and closes on a second press', () => {
    mount([notification(1)], 1);
    expect(flyout()).toBeNull();

    click(bell());
    expect(flyout()).not.toBeNull();

    click(bell());
    expect(flyout()).toBeNull();
  });

  it('closes on Escape and returns focus to the bell', () => {
    mount([notification(1)], 1);
    click(bell());
    expect(flyout()).not.toBeNull();

    escape();

    expect(flyout()).toBeNull();
    // Focus must not be left inside a panel that no longer exists.
    expect(document.activeElement).toBe(bell());
  });

  it('closes when the click lands outside it', () => {
    mount([notification(1)], 1);
    click(bell());
    expect(flyout()).not.toBeNull();

    click(document.body);

    expect(flyout()).toBeNull();
  });

  it('stays open when the click lands inside it', () => {
    mount([notification(1)], 1);
    click(bell());

    const row = fixture.nativeElement.querySelector('.notif-item') as HTMLElement;
    click(row);

    expect(flyout()).not.toBeNull();
  });

  it('does nothing on Escape while it is already closed', () => {
    mount();
    escape();
    expect(flyout()).toBeNull();
    expect(component.open()).toBeFalse();
  });

  it('shows the unread count, and no badge at all when everything is read', () => {
    mount([notification(1)], 3);
    expect(fixture.nativeElement.querySelector('.notif-badge')?.textContent?.trim()).toBe('3');

    fixture.componentRef.setInput('unreadCount', 0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.notif-badge')).toBeNull();
  });

  it('asks the parent to mark a notification read, rather than doing it itself', () => {
    mount([notification(7)], 1);
    const emitted: NotificationItem[] = [];
    component.markRead.subscribe((item) => emitted.push(item));

    click(bell());
    click(fixture.nativeElement.querySelector('.notif-item') as HTMLElement);

    expect(emitted.length).toBe(1);
    expect(emitted[0].id).toBe(7);
  });

  it('emits markAllRead from the header action', () => {
    mount([notification(1), notification(2)], 2);
    let called = 0;
    component.markAllRead.subscribe(() => called++);

    click(bell());
    click(fixture.nativeElement.querySelector('.link-btn-text') as HTMLElement);

    expect(called).toBe(1);
  });

  it('shows the empty state when there is nothing to report', () => {
    mount([], 0);
    click(bell());

    expect(fixture.nativeElement.querySelector('.notif-empty')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.notif-item')).toBeNull();
  });
});
