import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { filter } from 'rxjs/operators';

/**
 * Event type for Dynamic Actions.
 * Emulates APEX Refresh, Show, Hide, etc.
 */
export interface ApexDynamicActionEvent {
  action: 'refresh' | 'show' | 'hide' | 'enable' | 'disable' | 'custom';
  targetRegionId?: string;
  targetComponentId?: string;
  payload?: any;
}

@Injectable({
  providedIn: 'root'
})
export class ApexDynamicActionService {
  private _events$ = new Subject<ApexDynamicActionEvent>();

  /**
   * Broadcasts a Dynamic Action event to the application.
   */
  public broadcast(event: ApexDynamicActionEvent): void {
    this._events$.next(event);
  }

  /**
   * Observable to listen for ALL Dynamic Action events.
   */
  public get events$(): Observable<ApexDynamicActionEvent> {
    return this._events$.asObservable();
  }

  /**
   * Listens for a specific action on a specific target.
   * Useful for components to listen to 'refresh' events targeted at them.
   */
  public onAction(action: string, targetId: string): Observable<ApexDynamicActionEvent> {
    return this._events$.pipe(
      filter(e => e.action === action && (e.targetRegionId === targetId || e.targetComponentId === targetId))
    );
  }

  /**
   * Convenience method to trigger a refresh on a specific region.
   */
  public refreshRegion(regionId: string, payload?: any): void {
    this.broadcast({
      action: 'refresh',
      targetRegionId: regionId,
      payload
    });
  }
}
