import { Directive, HostListener, Input, inject } from '@angular/core';
import { ApexDynamicActionService } from './dynamic-action.service';

export interface ApexDynamicActionDefinition {
  event: 'click' | 'change' | 'dblclick' | 'mouseenter' | 'mouseleave';
  action: 'refresh' | 'show' | 'hide' | 'enable' | 'disable' | 'custom' | 'executeCode';
  targetId?: string;
  code?: () => void;
  condition?: () => boolean;
}

@Directive({
  selector: '[apexDA]',
  standalone: true
})
export class ApexDynamicActionDirective {
  /** The dynamic action definition */
  @Input('apexDA') daDef!: ApexDynamicActionDefinition;

  private daService = inject(ApexDynamicActionService);

  @HostListener('click', ['$event'])
  onClick(e: Event) {
    if (this.daDef?.event === 'click') {
      this.executeAction();
    }
  }

  @HostListener('change', ['$event'])
  onChange(e: Event) {
    if (this.daDef?.event === 'change') {
      this.executeAction();
    }
  }

  @HostListener('dblclick', ['$event'])
  onDblClick(e: Event) {
    if (this.daDef?.event === 'dblclick') {
      this.executeAction();
    }
  }

  @HostListener('mouseenter', ['$event'])
  onMouseEnter(e: Event) {
    if (this.daDef?.event === 'mouseenter') {
      this.executeAction();
    }
  }

  @HostListener('mouseleave', ['$event'])
  onMouseLeave(e: Event) {
    if (this.daDef?.event === 'mouseleave') {
      this.executeAction();
    }
  }

  private executeAction(): void {
    // Check Client-Side Condition
    if (this.daDef.condition && !this.daDef.condition()) {
      return; // Condition not met
    }

    if (this.daDef.action === 'executeCode' && this.daDef.code) {
      this.daDef.code();
    } else if (this.daDef.action !== 'executeCode') {
      // Broadcast to standard service
      this.daService.broadcast({
        action: this.daDef.action as any,
        targetRegionId: this.daDef.targetId,
        targetComponentId: this.daDef.targetId
      });
    }
  }
}
