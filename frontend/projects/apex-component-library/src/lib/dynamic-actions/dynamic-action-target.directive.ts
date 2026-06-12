import { Directive, ElementRef, Input, OnInit, OnDestroy, Renderer2, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { ApexDynamicActionService } from './dynamic-action.service';

@Directive({
  selector: '[apexDATarget]',
  standalone: true
})
export class ApexDynamicActionTargetDirective implements OnInit, OnDestroy {
  @Input('apexDATarget') targetId!: string;

  private el = inject(ElementRef);
  private renderer = inject(Renderer2);
  private daService = inject(ApexDynamicActionService);
  private sub?: Subscription;

  private originalDisplay = '';

  ngOnInit() {
    this.originalDisplay = this.el.nativeElement.style.display || '';

    this.sub = this.daService.events$.subscribe(event => {
      if (
        event.targetRegionId === this.targetId ||
        event.targetComponentId === this.targetId
      ) {
        this.handleAction(event.action, event.payload);
      }
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  private handleAction(action: string, payload?: any) {
    const nativeEl = this.el.nativeElement;

    switch (action) {
      case 'show':
        this.renderer.setStyle(nativeEl, 'display', this.originalDisplay);
        break;
      case 'hide':
        this.renderer.setStyle(nativeEl, 'display', 'none');
        break;
      case 'enable':
        this.renderer.removeAttribute(nativeEl, 'disabled');
        if ('disabled' in nativeEl) {
          nativeEl.disabled = false;
        }
        break;
      case 'disable':
        this.renderer.setAttribute(nativeEl, 'disabled', 'true');
        if ('disabled' in nativeEl) {
          nativeEl.disabled = true;
        }
        break;
      case 'refresh':
        // Dispatch custom refresh event for components to handle custom reload logic
        const refreshEvent = new CustomEvent('apex-refresh', { detail: payload });
        nativeEl.dispatchEvent(refreshEvent);
        break;
    }
  }
}
