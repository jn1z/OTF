package OTF.Model;

public record DeterminizeRecord<S>(S inputState, int outputAddress) {

  @Override
  public String toString() {
    return outputAddress + ": " + inputState;
  }
}