import { Component, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  onReset?: () => void;
}

interface State {
  hasError: boolean;
}

/**
 * Top-level safety net. Shows a generic recovery message only: error messages and stacks can contain
 * sensitive data and are never rendered.
 */
export class ErrorBoundary extends Component<Props, State> {
  override state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  private readonly reset = () => {
    this.setState({ hasError: false });
    this.props.onReset?.();
  };

  override render() {
    if (!this.state.hasError) {
      return this.props.children;
    }
    return (
      <div role="alert" className="error-boundary">
        <h1>Something went wrong</h1>
        <p>LawDesk hit an unexpected problem. Your saved work is not affected.</p>
        <p>
          <button type="button" onClick={this.reset}>
            Try again
          </button>{' '}
          <a href="/">Go to the home page</a>
        </p>
      </div>
    );
  }
}
