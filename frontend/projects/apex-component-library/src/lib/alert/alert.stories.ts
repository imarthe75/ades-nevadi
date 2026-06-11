import { Meta, StoryObj } from '@storybook/angular';
import { ApexAlertComponent } from './alert.component';

const meta: Meta<ApexAlertComponent> = {
  title: 'Components/Alert',
  component: ApexAlertComponent,
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Success: Story = {
  args: {
    severity: 'success',
    title: 'Success!',
    message: 'Operation completed successfully.',
    closable: true
  }
};

export const Error: Story = {
  args: {
    severity: 'error',
    title: 'Error!',
    message: 'Something went wrong. Please try again.',
    closable: true
  }
};
