import { Injectable } from '@angular/core';
import { MessageService } from 'primeng/api';

/**
 * Global Notification Service that emulates APEX's standard message display.
 * Requires MessageService from PrimeNG to be provided (usually at root or app module).
 */
@Injectable({
  providedIn: 'root'
})
export class ApexNotificationService {
  constructor(private messageService: MessageService) {}

  /**
   * Equivalent to APEX success message (e.g. "Action Processed")
   */
  public success(summary: string, detail?: string, life: number = 3000): void {
    this.messageService.add({ severity: 'success', summary, detail, life });
  }

  /**
   * Equivalent to APEX error message
   */
  public error(summary: string, detail?: string, life: number = 5000): void {
    this.messageService.add({ severity: 'error', summary, detail, life });
  }

  /**
   * Display a warning message
   */
  public warning(summary: string, detail?: string, life: number = 4000): void {
    this.messageService.add({ severity: 'warn', summary, detail, life });
  }

  /**
   * Display an informational message
   */
  public info(summary: string, detail?: string, life: number = 3000): void {
    this.messageService.add({ severity: 'info', summary, detail, life });
  }

  /**
   * Clears all currently displayed notifications
   */
  public clearAll(): void {
    this.messageService.clear();
  }
}
